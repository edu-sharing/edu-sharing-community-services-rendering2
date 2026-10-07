import assert from 'node:assert/strict'
import {test} from 'node:test'
import * as http from 'node:http'
import {S3PoolWatchdog} from '../src/s3Pool'
import {runInImportScope} from '../src/importScope'
import {installS3Middleware, Semaphore} from '../src/s3Throttle'

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

/** The two fields of an http.Agent the watchdog reads. */
const fakeAgent = (maxSockets: number, inUse: number, queued: number) => {
    const agent = new http.Agent({maxSockets})
    ;(agent as any).sockets = {origin: new Array(inUse).fill({})}
    ;(agent as any).requests = {origin: new Array(queued).fill({})}
    let destroyed = 0
    agent.destroy = () => {
        destroyed++
    }
    return {agent, destroyed: () => destroyed}
}

test('semaphore hands permits over in FIFO order', async () => {
    const semaphore = new Semaphore(1)
    const order: number[] = []
    await semaphore.acquire()
    const waiters = [1, 2, 3].map(n => semaphore.acquire().then(() => order.push(n)))
    semaphore.release()
    await sleep(5)
    semaphore.release()
    await sleep(5)
    semaphore.release()
    await Promise.all(waiters)
    assert.deepEqual(order, [1, 2, 3])
})

test('watchdog destroys the sockets of a full pool in which nothing settles', () => {
    let now = 0
    const {agent, destroyed} = fakeAgent(4, 4, 10)
    const watchdog = new S3PoolWatchdog([agent], {stallMs: 1_000, checkIntervalMs: 100, now: () => now})
    now = 500
    watchdog.check()
    assert.equal(destroyed(), 0, 'not stuck long enough yet')
    now = 1_600
    watchdog.check()
    assert.equal(destroyed(), 1)
    assert.equal(watchdog.status().recoveries, 1)
})

test('watchdog leaves a full pool alone while requests keep settling', () => {
    let now = 0
    const {agent, destroyed} = fakeAgent(4, 4, 10)
    const watchdog = new S3PoolWatchdog([agent], {stallMs: 1_000, checkIntervalMs: 100, now: () => now})
    for (let i = 0; i < 20; i++) {
        now += 400
        watchdog.noteProgress()
        watchdog.check()
    }
    assert.equal(destroyed(), 0)
    assert.equal(watchdog.status().stalled, false)
})

test('watchdog ignores a pool that is full but has nobody waiting, or is not full', () => {
    let now = 0
    for (const [inUse, queued] of [[4, 0], [3, 10]]) {
        const {agent, destroyed} = fakeAgent(4, inUse, queued)
        const watchdog = new S3PoolWatchdog([agent], {stallMs: 1_000, checkIntervalMs: 100, now: () => now})
        now += 5_000
        watchdog.check()
        now += 5_000
        watchdog.check()
        assert.equal(destroyed(), 0)
    }
})

test('only requests of an import wait for a permit, and every settled request counts as progress', async () => {
    const stack: Array<(next: any) => (args: any) => Promise<any>> = []
    const client = {middlewareStack: {add: (middleware: any) => stack.push(middleware)}}
    let progress = 0
    installS3Middleware(client, 2, {noteProgress: () => progress++} as unknown as S3PoolWatchdog)
    let inFlight = 0
    let peak = 0
    const handler = stack[0](async () => {
        inFlight++
        peak = Math.max(peak, inFlight)
        await sleep(10)
        inFlight--
        return 'done'
    })

    await Promise.all(Array.from({length: 8}, () => runInImportScope(() => handler({}))))
    assert.equal(peak, 2, 'an import is limited to its permits')

    inFlight = 0
    peak = 0
    await Promise.all(Array.from({length: 8}, () => handler({})))
    assert.equal(peak, 8, 'requests outside an import are not throttled')
    assert.equal(progress, 16)
})

test('a failing request releases its permit', async () => {
    const stack: Array<(next: any) => (args: any) => Promise<any>> = []
    installS3Middleware({middlewareStack: {add: (middleware: any) => stack.push(middleware)}}, 1)
    const handler = stack[0](async (args: any) => {
        if (args.fail) {
            throw new Error('s3 down')
        }
        return 'ok'
    })
    await assert.rejects(runInImportScope(() => handler({fail: true})), /s3 down/)
    assert.equal(await runInImportScope(() => handler({})), 'ok')
})

test('fullness is judged per agent: the unused one must not hide a full one', () => {
    let now = 0
    const full = fakeAgent(4, 4, 10)
    const unused = fakeAgent(4, 0, 0)
    const watchdog = new S3PoolWatchdog([unused.agent, full.agent], {stallMs: 1_000, checkIntervalMs: 100, now: () => now})
    now = 5_000
    watchdog.check()
    now = 7_000
    watchdog.check()
    assert.equal(full.destroyed(), 1)
    assert.equal(watchdog.status().maxSockets, 4, 'reports the limit of the agent in use, not the sum')
})

test('destroySockets frees the pool on demand, even when it is nowhere near full', () => {
    const {agent, destroyed} = fakeAgent(256, 1, 0)
    const watchdog = new S3PoolWatchdog([agent], {stallMs: 1_000, checkIntervalMs: 100})
    watchdog.destroySockets('test')
    assert.equal(destroyed(), 1)
    assert.equal(watchdog.status().recoveries, 1)
})
