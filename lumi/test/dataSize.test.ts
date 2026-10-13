import assert from 'node:assert/strict'
import {test} from 'node:test'
import {readDataSize} from '../src/dataSize'

test('readDataSize falls back when unset or empty', () => {
    assert.equal(readDataSize(undefined, 123), 123)
    assert.equal(readDataSize('  ', 123), 123)
})

test('readDataSize parses sizes with binary units', () => {
    assert.equal(readDataSize('2GB', 1), 2 * 1024 ** 3)
    assert.equal(readDataSize('1500mb', 1), 1500 * 1024 ** 2)
    assert.equal(readDataSize('4096', 1), 4096)
})

test('an invalid value keeps the fallback and is reported', () => {
    const errors: string[] = []
    assert.equal(readDataSize('lots', 777, e => errors.push(e.message)), 777)
    assert.equal(errors.length, 1)
})
