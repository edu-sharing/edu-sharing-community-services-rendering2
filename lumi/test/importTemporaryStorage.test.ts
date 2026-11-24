import assert from 'node:assert/strict'
import {test} from 'node:test'
import {promises as fs, existsSync} from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {Readable} from 'node:stream'
import {
    sweepImportTemporaryDirs, UnavailableTemporaryStorage, withImportTemporaryStorage
} from '../src/importTemporaryStorage'

const diskSource = {temporaryStorageMode: 'disk' as const, temporaryStorage: new UnavailableTemporaryStorage()}
const tempRoot = () => fs.mkdtemp(path.join(os.tmpdir(), 'lumi-test-'))

test('s3 mode hands out the shared storage and creates nothing', async () => {
    const root = await tempRoot()
    const shared = new UnavailableTemporaryStorage()
    const used = await withImportTemporaryStorage(
        {temporaryStorageMode: 's3', temporaryStorage: shared}, async storage => storage, root)
    assert.equal(used, shared)
    assert.deepEqual(await fs.readdir(root), [])
    await fs.rm(root, {recursive: true})
})

test('the directory is removed after success and after a throw', async () => {
    const root = await tempRoot()
    let dir = ''
    await withImportTemporaryStorage(diskSource, async storage => {
        const entries = await fs.readdir(root)
        assert.equal(entries.length, 1)
        dir = path.join(root, entries[0])
        await storage.saveFile('a.txt', Readable.from(['x']) as any, {id: 'u'} as any, new Date(Date.now() + 1000))
        assert.ok(existsSync(dir))
    }, root)
    assert.ok(!existsSync(dir))

    await assert.rejects(withImportTemporaryStorage(diskSource, async () => {
        throw new Error('import failed')
    }, root), /import failed/)
    assert.deepEqual(await fs.readdir(root), [])
    await fs.rm(root, {recursive: true})
})

test('@ survives in file names (the name becomes an S3 key)', async () => {
    const root = await tempRoot()
    await withImportTemporaryStorage(diskSource, async storage => {
        const user = {id: 'u'} as any
        const saved = await storage.saveFile('images/a@2x.png', Readable.from(['x']) as any, user,
            new Date(Date.now() + 1000))
        assert.equal(saved.filename, 'images/a@2x.png')
        assert.ok(await storage.fileExists('images/a@2x.png', user))
    }, root)
    await fs.rm(root, {recursive: true})
})

test('the sweep removes only lumi-import-* directories', async () => {
    const root = await tempRoot()
    await fs.mkdir(path.join(root, 'lumi-import-abc'))
    await fs.writeFile(path.join(root, 'lumi-import-abc', 'f'), 'x')
    await fs.mkdir(path.join(root, 'other'))
    await fs.writeFile(path.join(root, 'upload.h5p'), 'x')
    assert.equal(await sweepImportTemporaryDirs(root), 1)
    assert.deepEqual((await fs.readdir(root)).sort(), ['other', 'upload.h5p'])
    await fs.rm(root, {recursive: true})
})

test('the unavailable storage fails with a 500', async () => {
    await assert.rejects(new UnavailableTemporaryStorage().listFiles(), (error: any) => error.httpStatusCode === 500)
})
