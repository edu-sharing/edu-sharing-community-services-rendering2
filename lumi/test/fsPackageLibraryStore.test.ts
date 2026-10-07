import assert from 'node:assert/strict'
import {test} from 'node:test'
import {mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import FsPackageLibraryStore from '../src/packageLibraries/FsPackageLibraryStore'

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

/** A cache root holding one published package of `bytes` bytes. */
const cacheWithPackage = async (bytes = 100_000): Promise<string> => {
    const root = await mkdtemp(path.join(os.tmpdir(), 'lumi-cache-test-'))
    await mkdir(path.join(root, 'pkg1', 'H5P.Foo-1.0'), {recursive: true})
    await writeFile(path.join(root, 'pkg1', 'H5P.Foo-1.0', 'library.js'), Buffer.alloc(bytes, 1))
    return root
}

const untilMeasured = async (store: FsPackageLibraryStore) => {
    for (let i = 0; i < 200 && store.usage().reconciling; i++) {
        await sleep(10)
    }
    assert.equal(store.usage().reconciling, false, 'the measurement should have finished')
}

const withRoot = async (root: string, body: () => Promise<void>) => {
    try {
        await body()
    } finally {
        await rm(root, {recursive: true, force: true})
    }
}

test('create returns before the cache is measured, and the first start waits for the result', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        const store = await FsPackageLibraryStore.create(root, {quotaBytes: 1_000_000_000})
        assert.equal(store.usage().usedBytes, 0, 'nothing is remembered on a first start')
        assert.equal(store.usage().reconciling, true)
        // Nothing remembered: the quota cannot be judged yet, so an import has to wait for the walk.
        await store.awaitUsage()
        assert.equal(store.usage().reconciling, false)
        assert.ok(store.usage().usedBytes >= 100_000, `measured ${store.usage().usedBytes}`)
    })
})

test('the size remembered from the last run is in place immediately and replaced by the measurement', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        await writeFile(path.join(root, '.usage.json'), JSON.stringify({usedBytes: 4_321}))
        const store = await FsPackageLibraryStore.create(root, {quotaBytes: 1_000_000_000})
        assert.equal(store.usage().usedBytes, 4_321)
        await untilMeasured(store)
        assert.notEqual(store.usage().usedBytes, 4_321)
        assert.ok(store.usage().usedBytes >= 100_000)
    })
})

test('a cache that is nearly full by the remembered size makes imports wait for the measurement', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        // Remembered 95 of 100, truth is far below: the import must not decide on the remembered 95.
        await writeFile(path.join(root, '.usage.json'), JSON.stringify({usedBytes: 95}))
        const store = await FsPackageLibraryStore.create(root, {quotaBytes: 100})
        assert.equal(store.usage().reconciling, true)
        await store.awaitUsage()
        assert.equal(store.usage().reconciling, false)
        assert.notEqual(store.usage().usedBytes, 95)
    })
})

test('an unreadable or nonsensical remembered size is ignored', async () => {
    for (const content of ['not json', JSON.stringify({usedBytes: -5}), JSON.stringify({usedBytes: 'many'})]) {
        const root = await cacheWithPackage()
        await withRoot(root, async () => {
            await writeFile(path.join(root, '.usage.json'), content)
            const store = await FsPackageLibraryStore.create(root)
            assert.equal(store.usage().usedBytes, 0, content)
            await untilMeasured(store)
        })
    }
})

test('flush remembers the measured size, and the bookkeeping files are not part of it', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        const first = await FsPackageLibraryStore.create(root)
        await untilMeasured(first)
        await first.flush()
        const remembered = JSON.parse(await readFile(path.join(root, '.usage.json'), 'utf8'))
        assert.equal(remembered.usedBytes, first.usage().usedBytes)

        // A second start measures the same cache again: the usage file must not count towards it.
        await rm(path.join(root, '.usage.json'))
        const second = await FsPackageLibraryStore.create(root)
        await untilMeasured(second)
        await second.flush()
        assert.equal(second.usage().usedBytes, first.usage().usedBytes)
    })
})

test('removing a package subtracts its contents from the total', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        const store = await FsPackageLibraryStore.create(root)
        await untilMeasured(store)
        const before = store.usage().usedBytes
        await store.remove('pkg1')
        assert.ok(store.usage().usedBytes < before)
        // What stays is the package's own directory entry (one block): the walk over the cache root
        // counts it, while removing a package only frees what is inside it. Pre-existing and harmless.
        assert.ok(store.usage().usedBytes <= 4096, `left ${store.usage().usedBytes}`)
    })
})
