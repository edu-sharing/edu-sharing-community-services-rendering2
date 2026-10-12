import assert from 'node:assert/strict'
import {test} from 'node:test'
import {DEFAULT_SHUTDOWN_TIMEOUT_MS, readShutdownTimeoutMs} from '../src/shutdown'

test('readShutdownTimeoutMs falls back to the default when unset or blank', () => {
    assert.equal(readShutdownTimeoutMs(undefined), DEFAULT_SHUTDOWN_TIMEOUT_MS)
    assert.equal(readShutdownTimeoutMs('  '), DEFAULT_SHUTDOWN_TIMEOUT_MS)
})

test('readShutdownTimeoutMs reads a positive number of milliseconds', () => {
    assert.equal(readShutdownTimeoutMs('105000'), 105000)
})

test('readShutdownTimeoutMs rejects invalid or non-positive values', () => {
    assert.equal(readShutdownTimeoutMs('abc'), DEFAULT_SHUTDOWN_TIMEOUT_MS)
    assert.equal(readShutdownTimeoutMs('0'), DEFAULT_SHUTDOWN_TIMEOUT_MS)
    assert.equal(readShutdownTimeoutMs('-5'), DEFAULT_SHUTDOWN_TIMEOUT_MS)
})
