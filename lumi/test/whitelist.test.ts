import assert from 'node:assert/strict'
import {test} from 'node:test'
import {extendWhitelist} from '../src/whitelist'

test('extendWhitelist keeps the defaults when nothing is configured', () => {
    assert.equal(extendWhitelist('json png', undefined), 'json png')
    assert.equal(extendWhitelist('json png', '  '), 'json png')
})

test('extendWhitelist appends new extensions, tolerating dots, commas and case', () => {
    assert.equal(extendWhitelist('json png', 'svg, .WebP mov'), 'json png svg webp mov')
})

test('extendWhitelist ignores duplicates and malformed entries', () => {
    assert.equal(extendWhitelist('json png', 'PNG svg svg ../x a/b'), 'json png svg')
})
