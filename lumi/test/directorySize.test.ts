import assert from 'node:assert/strict'
import {test} from 'node:test'
import {mkdir, mkdtemp, rm, writeFile} from 'node:fs/promises'
import {readdirSync, statSync} from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import {directorySize} from '../src/packageLibraries/FsPackageLibraryStore'

/** What `du` would add up: allocated blocks of everything below `dir`, directories included. */
const expectedSize = (dir: string, skipTopLevel: string[] = []): number => {
    let total = 0
    for (const name of readdirSync(dir)) {
        if (skipTopLevel.includes(name)) {
            continue
        }
        const full = path.join(dir, name)
        const stats = statSync(full)
        total += stats.blocks * 512
        if (stats.isDirectory()) {
            total += expectedSize(full)
        }
    }
    return total
}

const withTree = async (body: (root: string) => Promise<void>) => {
    const root = await mkdtemp(path.join(os.tmpdir(), 'lumi-dirsize-test-'))
    try {
        await body(root)
    } finally {
        await rm(root, {recursive: true, force: true})
    }
}

test('directorySize adds up the allocated blocks of a tree wider than the fan-out', async () => {
    await withTree(async root => {
        // 40 packages > the fan-out of 16, 3 libraries each, 40 files each (also wider than the fan-out)
        for (let p = 0; p < 40; p++) {
            for (let l = 0; l < 3; l++) {
                const dir = path.join(root, `pkg${p}`, `Lib${l}-1.0`)
                await mkdir(dir, {recursive: true})
                for (let f = 0; f < 40; f++) {
                    await writeFile(path.join(dir, `f${f}.js`), 'x'.repeat(100 + f * 37))
                }
            }
        }
        assert.equal(await directorySize(root), expectedSize(root))
    })
})

test('directorySize skips the given names directly inside the root, but only there', async () => {
    await withTree(async root => {
        await mkdir(path.join(root, '.staging'), {recursive: true})
        await writeFile(path.join(root, '.staging', 'big'), 'x'.repeat(50_000))
        await mkdir(path.join(root, 'pkg', '.staging'), {recursive: true})
        await writeFile(path.join(root, 'pkg', '.staging', 'kept'), 'x'.repeat(50_000))
        assert.equal(await directorySize(root, ['.staging']), expectedSize(root, ['.staging']))
        assert.ok(await directorySize(root, ['.staging']) < await directorySize(root))
    })
})

test('directorySize of a missing directory is 0', async () => {
    assert.equal(await directorySize(path.join(os.tmpdir(), 'lumi-does-not-exist-' + Date.now())), 0)
})
