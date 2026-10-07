import assert from 'node:assert/strict'
import {test} from 'node:test'
import {ImportDeadlineError, ImportQueue} from '../src/importQueue'
import {inImportScope} from '../src/importScope'

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))
const never = () => new Promise<never>(() => undefined)

test('runs tasks one at a time, in order, inside the import scope', async () => {
    const queue = new ImportQueue({timeoutMs: 0, releaseOnTimeout: true})
    const events: string[] = []
    const task = (name: string) => async () => {
        assert.equal(inImportScope(), true)
        events.push(`${name}:start`)
        await sleep(10)
        events.push(`${name}:end`)
        return name
    }
    const results = await Promise.all([queue.run(task('a')), queue.run(task('b')), queue.run(task('c'))])
    assert.deepEqual(results, ['a', 'b', 'c'])
    assert.deepEqual(events, ['a:start', 'a:end', 'b:start', 'b:end', 'c:start', 'c:end'])
    assert.equal(inImportScope(), false)
})

test('a failing task does not block the ones behind it', async () => {
    const queue = new ImportQueue({timeoutMs: 0, releaseOnTimeout: true})
    const failing = queue.run(async () => {
        throw new Error('boom')
    })
    const next = queue.run(async () => 'ok')
    await assert.rejects(failing, /boom/)
    assert.equal(await next, 'ok')
})

test('a task that never returns is given up on, and the queue moves on (per-package mode)', async () => {
    const queue = new ImportQueue({timeoutMs: 30, releaseOnTimeout: true})
    const stuck = queue.run(never)
    const next = queue.run(async () => 'next')
    await assert.rejects(stuck, ImportDeadlineError)
    assert.equal(await next, 'next')
    assert.equal(queue.status().deadlineExceeded, 1)
    assert.equal(queue.status().running, false)
})

test('global mode: caller is answered at the deadline, but the queue keeps waiting for the task', async () => {
    const queue = new ImportQueue({timeoutMs: 30, releaseOnTimeout: false})
    let finishStuck!: () => void
    const stuck = queue.run(() => new Promise<void>(resolve => {
        finishStuck = resolve
    }))
    let nextStarted = false
    const next = queue.run(async () => {
        nextStarted = true
        return 'next'
    })
    await assert.rejects(stuck, ImportDeadlineError)
    await sleep(50)
    assert.equal(nextStarted, false, 'overlapping imports corrupt the shared library storage')
    assert.equal(queue.status().overdue, true)
    finishStuck()
    assert.equal(await next, 'next')
    assert.equal(queue.status().overdue, false)
})

test('status reports waiting imports and the running time', async () => {
    let now = 1_000
    const queue = new ImportQueue({timeoutMs: 0, releaseOnTimeout: true, now: () => now})
    let finish!: () => void
    const first = queue.run(() => new Promise<void>(resolve => {
        finish = resolve
    }))
    const second = queue.run(async () => undefined)
    await sleep(5)
    now = 4_000
    assert.deepEqual(
        {...queue.status()},
        {waiting: 1, running: true, runningForMs: 3_000, overdue: false, deadlineExceeded: 0})
    finish()
    await Promise.all([first, second])
    assert.equal(queue.status().waiting, 0)
})

test('the deadline hook runs before the caller is answered, and a failing hook changes nothing', async () => {
    const calls: string[] = []
    const queue = new ImportQueue({
        timeoutMs: 20, releaseOnTimeout: true,
        onDeadline: () => {
            calls.push('hook')
            throw new Error('hook broke')
        }
    })
    await assert.rejects(queue.run(never).finally(() => calls.push('answered')), ImportDeadlineError)
    assert.deepEqual(calls, ['hook', 'answered'])
    assert.equal(await queue.run(async () => 'next'), 'next')
})
