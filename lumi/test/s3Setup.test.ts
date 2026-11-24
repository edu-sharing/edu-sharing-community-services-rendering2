import assert from 'node:assert/strict'
import {test} from 'node:test'
import {planS3Setup, readS3Scopes} from '../src/s3Scopes'

// Plans are built from a plain env object so the env parsing is covered as well.
const base = {
    LIBRARY_AWS_S3_BUCKET: 'library',
    CONTENT_AWS_S3_BUCKET: 'content',
    TEMPORARY_AWS_S3_BUCKET: 'temp'
}
const plan = (extra: Record<string, string> = {}) => planS3Setup(readS3Scopes({...base, ...extra}))

test('without any new setting the setup is unchanged', () => {
    const p = plan()
    // Order matters: it is the order the buckets have always been ensured in.
    assert.deepEqual(p.ensureBuckets, ['library', 'content', 'temp'])
    assert.deepEqual(p.headOnlyBuckets, [])
    assert.equal(p.setTemporaryLifecycle, true)
    assert.equal(p.temporaryStorage, 's3')
    // Empty prefixes mean both storages share the plain client.
    assert.equal(p.contentClientPrefix, '')
    assert.equal(p.libraryClientPrefix, '')
})

test('AWS_S3_MANAGE_BUCKETS=false creates nothing and sets no lifecycle', () => {
    const p = plan({AWS_S3_MANAGE_BUCKETS: 'false'})
    assert.deepEqual(p.ensureBuckets, [])
    assert.deepEqual(p.headOnlyBuckets, ['library', 'content', 'temp'])
    assert.equal(p.setTemporaryLifecycle, false)
})

test('disk temp storage leaves the temp bucket alone', () => {
    const managed = plan({H5P_TEMPORARY_STORAGE: 'disk'})
    assert.deepEqual(managed.ensureBuckets, ['library', 'content'])
    assert.deepEqual(managed.headOnlyBuckets, [])
    assert.equal(managed.setTemporaryLifecycle, false)
    assert.equal(managed.temporaryStorage, 'disk')

    const unmanaged = plan({H5P_TEMPORARY_STORAGE: 'disk', AWS_S3_MANAGE_BUCKETS: 'false'})
    assert.deepEqual(unmanaged.ensureBuckets, [])
    assert.deepEqual(unmanaged.headOnlyBuckets, ['library', 'content'])
    assert.equal(unmanaged.setTemporaryLifecycle, false)
})

test('key prefixes are normalized per storage', () => {
    const p = plan({CONTENT_AWS_S3_KEY_PREFIX: '/lumi/content', LIBRARY_AWS_S3_KEY_PREFIX: 'lumi/libs//'})
    assert.equal(p.contentClientPrefix, 'lumi/content/')
    assert.equal(p.libraryClientPrefix, 'lumi/libs/')
    // Prefixes do not change which buckets are managed.
    assert.deepEqual(p.ensureBuckets, ['library', 'content', 'temp'])
    assert.equal(plan({CONTENT_AWS_S3_KEY_PREFIX: 'only'}).libraryClientPrefix, '')
})

test('customer setup: one shared bucket, both prefixes, unmanaged, disk temp', () => {
    const p = planS3Setup(readS3Scopes({
        CONTENT_AWS_S3_BUCKET: 'shared',
        LIBRARY_AWS_S3_BUCKET: 'shared',
        CONTENT_AWS_S3_KEY_PREFIX: 'lumi/content',
        LIBRARY_AWS_S3_KEY_PREFIX: 'lumi/libraries',
        AWS_S3_MANAGE_BUCKETS: 'false',
        H5P_TEMPORARY_STORAGE: 'disk'
    }))
    assert.deepEqual(p.ensureBuckets, [])
    assert.deepEqual(p.headOnlyBuckets, ['shared', 'shared'])
    assert.equal(p.setTemporaryLifecycle, false)
    assert.equal(p.temporaryStorage, 'disk')
    assert.equal(p.contentClientPrefix, 'lumi/content/')
    assert.equal(p.libraryClientPrefix, 'lumi/libraries/')
})
