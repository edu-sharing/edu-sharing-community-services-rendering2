import assert from 'node:assert/strict'
import {test} from 'node:test'
import {mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import FsPackageLibraryStore from '../src/packageLibraries/FsPackageLibraryStore'

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))
const DAY = 24 * 3_600_000

/** A cache root holding one published package of `bytes` bytes. */
const cacheWithPackage = async (bytes = 100_000): Promise<string> => {
    const root = await mkdtemp(path.join(os.tmpdir(), 'lumi-cache-test-'))
    await mkdir(path.join(root, 'pkg1', 'H5P.Foo-1.0'), {recursive: true})
    await writeFile(path.join(root, 'pkg1', 'H5P.Foo-1.0', 'library.js'), Buffer.alloc(bytes, 1))
    return root
}

const remember = (root: string, usedBytes: number, measuredAgoMs?: number) =>
    writeFile(path.join(root, '.usage.json'), JSON.stringify({
        usedBytes,
        measuredAt: measuredAgoMs === undefined ? undefined : new Date(Date.now() - measuredAgoMs).toISOString()
    }))

const untilMeasured = async (store: FsPackageLibraryStore) => {
    for (let i = 0; i < 300 && store.usage().reconciling; i++) {
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

test('a first start returns at once, reports the size as unknown and measures in the background', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        const store = await FsPackageLibraryStore.create(root, {rescanIntervalMs: DAY})
        assert.equal(store.usage().known, false, 'there is nothing to report yet')
        assert.equal(store.usage().reconciling, true)
        await untilMeasured(store)
        assert.equal(store.usage().known, true)
        assert.ok(store.usage().usedBytes >= 100_000, `measured ${store.usage().usedBytes}`)
    })
})

test('a recent measurement is taken over and the cache is not measured again at start', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        await remember(root, 4_321, 60_000)
        const store = await FsPackageLibraryStore.create(root, {rescanIntervalMs: DAY})
        assert.deepEqual(
            {usedBytes: store.usage().usedBytes, known: store.usage().known, reconciling: store.usage().reconciling},
            {usedBytes: 4_321, known: true, reconciling: false})
        await sleep(100)
        assert.equal(store.usage().usedBytes, 4_321, 'no measurement may replace it')
    })
})

test('an old measurement is used meanwhile and replaced by a new one', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        await remember(root, 4_321, 2 * DAY)
        const store = await FsPackageLibraryStore.create(root, {rescanIntervalMs: DAY})
        assert.equal(store.usage().usedBytes, 4_321)
        assert.equal(store.usage().known, true, 'the remembered size is reported while measuring')
        assert.equal(store.usage().reconciling, true)
        await untilMeasured(store)
        assert.ok(store.usage().usedBytes >= 100_000)
    })
})

test('a remembered size without a measurement time counts as old', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        await remember(root, 4_321)
        const store = await FsPackageLibraryStore.create(root, {rescanIntervalMs: DAY})
        assert.equal(store.usage().reconciling, true)
        await untilMeasured(store)
    })
})

test('with an interval of 0 the cache is measured at every start', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        await remember(root, 4_321, 1_000)
        const store = await FsPackageLibraryStore.create(root, {rescanIntervalMs: 0})
        assert.equal(store.usage().reconciling, true)
        await untilMeasured(store)
        assert.ok(store.usage().usedBytes >= 100_000)
    })
})

test('the cache is measured again once the interval has passed while running', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        await remember(root, 4_321, 0)
        const store = await FsPackageLibraryStore.create(root, {rescanIntervalMs: 150})
        assert.equal(store.usage().usedBytes, 4_321)
        for (let i = 0; i < 100 && store.usage().usedBytes === 4_321; i++) {
            await sleep(20)
        }
        await untilMeasured(store)
        assert.ok(store.usage().usedBytes >= 100_000, `still ${store.usage().usedBytes}`)
    })
})

test('an unreadable or nonsensical remembered size is ignored', async () => {
    for (const content of ['not json', JSON.stringify({usedBytes: -5}), JSON.stringify({usedBytes: 'many'})]) {
        const root = await cacheWithPackage()
        await withRoot(root, async () => {
            await writeFile(path.join(root, '.usage.json'), content)
            const store = await FsPackageLibraryStore.create(root, {rescanIntervalMs: DAY})
            assert.equal(store.usage().known, false, content)
            await untilMeasured(store)
        })
    }
})

test('flush remembers the measured size and when it was measured, and the bookkeeping files are not part of it', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        const first = await FsPackageLibraryStore.create(root, {rescanIntervalMs: DAY})
        await untilMeasured(first)
        await first.flush()
        const remembered = JSON.parse(await readFile(path.join(root, '.usage.json'), 'utf8'))
        assert.equal(remembered.usedBytes, first.usage().usedBytes)
        assert.ok(Math.abs(Date.parse(remembered.measuredAt) - Date.now()) < 60_000)

        // A second start measures the same cache again: the usage file must not count towards it.
        await rm(path.join(root, '.usage.json'))
        const second = await FsPackageLibraryStore.create(root, {rescanIntervalMs: DAY})
        await untilMeasured(second)
        assert.equal(second.usage().usedBytes, first.usage().usedBytes)
    })
})

test('the quota is soft: publishing a package beyond it succeeds', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        // Far below the 100 kB package that is already there, far above what is left of it after removing it
        // (its directory entry, one block).
        const store = await FsPackageLibraryStore.create(root, {quotaBytes: 20_000, rescanIntervalMs: DAY})
        await untilMeasured(store)
        const staging = await store.createStaging()
        await writeFile(path.join(root, '.staging', staging.id, 'lib.js'), Buffer.alloc(50_000, 1))
        const bytes = await store.promote(staging, 'pkg2')
        assert.ok(bytes >= 50_000)
        assert.ok(store.usage().usedBytes > store.usage().quotaBytes, 'over the quota, and nothing refused it')
        assert.equal(store.usage().overQuota, true, 'but it is signalled, for the metric and the alert')

        await store.remove('pkg2')
        await store.remove('pkg1')
        assert.equal(store.usage().overQuota, false, 'and cleared once the cache is back under the quota')
    })
})

test('over-quota is only signalled for a known size and a configured quota', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        const unknown = await FsPackageLibraryStore.create(root, {quotaBytes: 10, rescanIntervalMs: DAY})
        assert.equal(unknown.usage().known, false)
        assert.equal(unknown.usage().overQuota, false, 'a size that is still being measured proves nothing')
        await untilMeasured(unknown)
        assert.equal(unknown.usage().overQuota, true)

        const noQuota = await FsPackageLibraryStore.create(root, {rescanIntervalMs: DAY})
        await untilMeasured(noQuota)
        assert.equal(noQuota.usage().overQuota, false, 'no quota, nothing to exceed')
    })
})

test('removing a package subtracts its contents from the total', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        const store = await FsPackageLibraryStore.create(root, {rescanIntervalMs: DAY})
        await untilMeasured(store)
        const before = store.usage().usedBytes
        await store.remove('pkg1')
        assert.ok(store.usage().usedBytes < before)
        // What stays is the package's own directory entry (one block): the walk over the cache root
        // counts it, while removing a package only frees what is inside it. Pre-existing and harmless.
        assert.ok(store.usage().usedBytes <= 4096, `left ${store.usage().usedBytes}`)
    })
})

test('a size that is not known yet is never written down', async () => {
    const root = await cacheWithPackage()
    await withRoot(root, async () => {
        const store = await FsPackageLibraryStore.create(root, {rescanIntervalMs: DAY})
        // Right after the start the first measurement cannot have finished: the total is only the changes so far.
        assert.equal(store.usage().known, false)
        ;(store as any).markDirty()
        await store.flush()
        await assert.rejects(readFile(path.join(root, '.usage.json')), 'nothing may be remembered yet')

        await untilMeasured(store)
        await store.flush()
        const remembered = JSON.parse(await readFile(path.join(root, '.usage.json'), 'utf8'))
        assert.equal(remembered.usedBytes, store.usage().usedBytes)
    })
})
