/**
 * Runs the key-prefix middleware inside the REAL AWS SDK v3 client (no network): the client is built
 * the way lumi builds it (h5p-mongos3's initS3) and only its request handler is replaced by a stub that
 * records the wire request and answers with canned responses. The sibling s3KeyPrefix.test.ts uses a
 * fake middleware stack and therefore cannot prove step order, commandName, serialization or lib-storage.
 */
import assert from 'node:assert/strict'
import {test} from 'node:test'
import {Readable} from 'node:stream'
import * as dbImplementations from '@lumieducation/h5p-mongos3'
// Resolve the very copies the client uses (hoisted next to it), not whatever sits at the top level.
// eslint-disable-next-line @typescript-eslint/no-var-requires
const clientRequire = require('module').createRequire(require.resolve('@aws-sdk/client-s3'))
const {HttpResponse} = clientRequire('@smithy/protocol-http')
const {GetObjectCommand, PutObjectCommand, HeadObjectCommand, DeleteObjectCommand, DeleteObjectsCommand, ListObjectsV2Command} =
    clientRequire('@aws-sdk/client-s3')
const {Upload} = require('module').createRequire(require.resolve('@lumieducation/h5p-mongos3'))('@aws-sdk/lib-storage')
import {installS3KeyPrefix} from '../src/s3KeyPrefix'

interface Recorded {
    method: string
    path: string
    query: Record<string, any>
    headers: Record<string, string>
    body: string
}

type Responder = (request: any, index: number) => {statusCode?: number, headers?: Record<string, string>, body?: string}

const xml = (body: string) => ({headers: {'content-type': 'application/xml'}, body})

/** Real client + stub handler; `respond` produces the canned answer for the n-th request. */
const setup = (prefix: string, respond: Responder = () => ({})) => {
    const requests: Recorded[] = []
    const requestHandler = {
        metadata: {handlerProtocol: 'http/1.1'},
        destroy() {
        },
        updateHttpClientConfig() {
        },
        httpHandlerConfigs: () => ({}),
        async handle(request: any) {
            let body = ''
            if (typeof request.body === 'string') {
                body = request.body
            } else if (request.body instanceof Uint8Array) {
                body = Buffer.from(request.body).toString('latin1')
            } else if (request.body && typeof request.body[Symbol.asyncIterator] === 'function') {
                const chunks: Buffer[] = []
                for await (const chunk of request.body) {
                    chunks.push(Buffer.from(chunk))
                }
                body = Buffer.concat(chunks).toString('latin1')
            }
            const index = requests.length
            requests.push({
                method: request.method, path: request.path, query: {...request.query},
                headers: {...request.headers}, body
            })
            const canned = respond(request, index)
            return {
                response: new HttpResponse({
                    statusCode: canned.statusCode ?? 200,
                    headers: canned.headers ?? {},
                    body: Readable.from([Buffer.from(canned.body ?? '')])
                })
            }
        }
    }
    const client = dbImplementations.initS3({
        credentials: {accessKeyId: 'AK', secretAccessKey: 'SK'},
        endpoint: 'http://s3.test',
        region: 'us-east-1',
        forcePathStyle: true,
        requestHandler
    } as any)
    installS3KeyPrefix(client as any, prefix)
    return {client: client as any, requests}
}

const PREFIX = 'lumi/content/'

test('without the middleware (empty prefix) the paths are unprefixed', async () => {
    const {client, requests} = setup('')
    await client.send(new HeadObjectCommand({Bucket: 'bucket', Key: 'a/b.png'}))
    await client.send(new ListObjectsV2Command({Bucket: 'bucket', Prefix: 'a/'}))
    assert.equal(requests[0].path, '/bucket/a/b.png')
    assert.equal(requests[1].query.prefix, 'a/')
})

test('GetObject, HeadObject, DeleteObject and PutObject address the prefixed key', async () => {
    const {client, requests} = setup(PREFIX)
    await client.send(new GetObjectCommand({Bucket: 'bucket', Key: 'a/b.png'}))
    await client.send(new HeadObjectCommand({Bucket: 'bucket', Key: 'a/b.png'}))
    await client.send(new DeleteObjectCommand({Bucket: 'bucket', Key: 'a/b.png'}))
    await client.send(new PutObjectCommand({Bucket: 'bucket', Key: 'a/c.txt', Body: 'hello'}))
    assert.deepEqual(requests.map(r => [r.method, r.path]), [
        ['GET', '/bucket/lumi/content/a/b.png'],
        ['HEAD', '/bucket/lumi/content/a/b.png'],
        ['DELETE', '/bucket/lumi/content/a/b.png'],
        ['PUT', '/bucket/lumi/content/a/c.txt']
    ])
})

test('DeleteObjects carries the prefixed keys in the XML body', async () => {
    const {client, requests} = setup(PREFIX, () => xml('<DeleteResult><Deleted><Key>lumi/content/a/1</Key></Deleted></DeleteResult>'))
    const input = {Bucket: 'bucket', Delete: {Objects: [{Key: 'a/1'}, {Key: 'a/2'}]}}
    await client.send(new DeleteObjectsCommand(input))
    assert.equal(requests[0].method, 'POST')
    assert.ok(requests[0].body.includes('<Key>lumi/content/a/1</Key>'), requests[0].body)
    assert.ok(requests[0].body.includes('<Key>lumi/content/a/2</Key>'), requests[0].body)
    assert.deepEqual(input.Delete.Objects, [{Key: 'a/1'}, {Key: 'a/2'}], 'the caller\'s params stay untouched')
})

const LIST_RESPONSE = (keys: string[], extra = '') => xml(
    `<?xml version="1.0" encoding="UTF-8"?><ListBucketResult><Name>bucket</Name><Prefix>${PREFIX}a/</Prefix>` +
    `<KeyCount>${keys.length}</KeyCount><MaxKeys>1000</MaxKeys><IsTruncated>false</IsTruncated>` +
    keys.map(k => `<Contents><Key>${k}</Key><Size>1</Size></Contents>`).join('') + extra +
    '</ListBucketResult>')

test('ListObjectsV2 sends the prefixed Prefix and strips it from the result', async () => {
    const {client, requests} = setup(PREFIX, () => LIST_RESPONSE(
        [`${PREFIX}a/x.png`, `${PREFIX}a/y.png`],
        `<CommonPrefixes><Prefix>${PREFIX}a/sub/</Prefix></CommonPrefixes>`))
    const out = await client.send(new ListObjectsV2Command({Bucket: 'bucket', Prefix: 'a/'}))
    assert.equal(requests[0].query.prefix, 'lumi/content/a/')
    assert.deepEqual(out.Contents.map((c: any) => c.Key), ['a/x.png', 'a/y.png'])
    assert.deepEqual(out.CommonPrefixes.map((c: any) => c.Prefix), ['a/sub/'])
    assert.equal(out.Prefix, 'a/')
})

test('ListObjectsV2 without a Prefix is confined to the bare prefix', async () => {
    const {client, requests} = setup(PREFIX, () => LIST_RESPONSE([]))
    await client.send(new ListObjectsV2Command({Bucket: 'bucket'}))
    assert.equal(requests[0].query.prefix, PREFIX)
})

test('lib-storage Upload (single part) writes to the prefixed key', async () => {
    const {client, requests} = setup(PREFIX)
    await new Upload({client, params: {Bucket: 'bucket', Key: 'c1/file.bin', Body: Buffer.from('data')}}).done()
    assert.equal(requests.length, 1)
    assert.equal(requests[0].method, 'PUT')
    assert.equal(requests[0].path, '/bucket/lumi/content/c1/file.bin')
})

test('lib-storage multipart Upload carries the prefixed key on every request', async () => {
    const {client, requests} = setup(PREFIX, (request) => {
        if (request.method === 'POST' && 'uploads' in request.query) {
            return xml('<InitiateMultipartUploadResult><Bucket>bucket</Bucket><Key>x</Key><UploadId>U1</UploadId></InitiateMultipartUploadResult>')
        }
        if (request.method === 'PUT') {
            return {headers: {etag: `"e${request.query.partNumber}"`}}
        }
        return xml('<CompleteMultipartUploadResult><Bucket>bucket</Bucket><Key>x</Key><ETag>"z"</ETag></CompleteMultipartUploadResult>')
    })
    await new Upload({
        client, params: {Bucket: 'bucket', Key: 'c1/big.bin', Body: Buffer.alloc(6 * 1024 * 1024, 1)}
    }).done()
    assert.ok(requests.length >= 4, `create + 2 parts + complete expected, got ${requests.length}`)
    for (const r of requests) {
        assert.equal(r.path, '/bucket/lumi/content/c1/big.bin', `${r.method} ${r.path}`)
    }
    assert.ok(requests.some(r => r.method === 'PUT' && r.query.partNumber !== undefined))
})

test('MongoS3ContentStorage.listFiles returns unprefixed filenames over the prefixed client', async () => {
    const contentId = '0123456789abcdef01234567'
    const {client, requests} = setup(PREFIX, () => LIST_RESPONSE(
        [`${PREFIX}${contentId}/content.json`, `${PREFIX}${contentId}/images/a.png`]))
    const storage = new dbImplementations.MongoS3ContentStorage(client, {} as any, {s3Bucket: 'bucket'} as any)
    const files = await storage.listFiles(contentId, undefined as any)
    assert.equal(requests[0].query.prefix, `${PREFIX}${contentId}/`)
    assert.deepEqual(files, ['content.json', 'images/a.png'])
})
