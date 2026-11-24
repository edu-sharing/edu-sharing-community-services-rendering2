import assert from 'node:assert/strict'
import {test} from 'node:test'
import {
    normalizeKeyPrefix, readS3Scopes, S3Scopes, validateCollectionNames, validateS3Scopes
} from '../src/s3Scopes'

const scopes = (overrides: Partial<S3Scopes>): S3Scopes => ({
    contentBucket: 'content', libraryBucket: 'library', temporaryBucket: 'temp',
    contentKeyPrefix: '', libraryKeyPrefix: '', manageBuckets: true, temporaryStorage: 's3',
    ...overrides
})

test('prefixes are normalized like the service does', () => {
    assert.equal(normalizeKeyPrefix(undefined), '')
    assert.equal(normalizeKeyPrefix('  '), '')
    assert.equal(normalizeKeyPrefix('/'), '')
    assert.equal(normalizeKeyPrefix('lumi'), 'lumi/')
    assert.equal(normalizeKeyPrefix(' /a/b// '), 'a/b/')
})

test('defaults are today\'s behaviour', () => {
    const read = readS3Scopes({CONTENT_AWS_S3_BUCKET: 'c'})
    assert.equal(read.manageBuckets, true)
    assert.equal(read.temporaryStorage, 's3')
    assert.equal(read.contentKeyPrefix, '')
    assert.equal(readS3Scopes({AWS_S3_MANAGE_BUCKETS: 'false', H5P_TEMPORARY_STORAGE: 'disk'}).manageBuckets, false)
    assert.equal(readS3Scopes({H5P_TEMPORARY_STORAGE: 'disk'}).temporaryStorage, 'disk')
})

test('separate buckets are fine', () => {
    assert.deepEqual(validateS3Scopes(scopes({})), {fatal: [], warnings: []})
})

test('s3 temp storage in a shared bucket with bucket management is fatal', () => {
    assert.equal(validateS3Scopes(scopes({temporaryBucket: 'content'})).fatal.length, 1)
    assert.equal(validateS3Scopes(scopes({temporaryBucket: 'library'})).fatal.length, 1)
})

test('the same without bucket management only warns; disk temp storage ignores the temp bucket', () => {
    const unmanaged = validateS3Scopes(scopes({temporaryBucket: 'content', manageBuckets: false}))
    assert.equal(unmanaged.fatal.length, 0)
    assert.equal(unmanaged.warnings.length, 1)
    assert.deepEqual(validateS3Scopes(scopes({temporaryBucket: 'content', temporaryStorage: 'disk'})),
        {fatal: [], warnings: []})
})

test('content and library overlap in one bucket warns', () => {
    const shared = {contentBucket: 'one', libraryBucket: 'one', temporaryStorage: 'disk'} as const
    assert.equal(validateS3Scopes(scopes({...shared})).warnings.length, 1, 'both empty')
    assert.equal(validateS3Scopes(scopes({...shared, contentKeyPrefix: 'lumi/'})).warnings.length, 1, 'one empty')
    assert.equal(validateS3Scopes(scopes({...shared, contentKeyPrefix: 'lumi/', libraryKeyPrefix: 'lumi/lib/'})).warnings.length, 1, 'nested')
    assert.equal(validateS3Scopes(scopes({...shared, contentKeyPrefix: 'lumi/c/', libraryKeyPrefix: 'lumi/l/'})).warnings.length, 0, 'disjoint')
})

test('collection names must not hit a service collection or each other', () => {
    assert.deepEqual(validateCollectionNames({A: 'h5pContent', B: 'h5pLibraries', C: 'edusharing'}), [])
    assert.equal(validateCollectionNames({A: 'Tracking', B: 'x'}).length, 1)
    assert.equal(validateCollectionNames({A: 'x', B: 'x'}).length, 1)
    assert.deepEqual(validateCollectionNames({A: undefined, B: undefined}), [])
})
