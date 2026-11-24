import assert from 'node:assert/strict'
import {test} from 'node:test'
import {installS3KeyPrefix} from '../src/s3KeyPrefix'

type Middleware = (next: (args: any) => Promise<any>, context: any) => (args: any) => Promise<any>

/** Installs the prefixing on a fake stack; returns a function that sends an input through it. */
const setup = (prefix: string, commandName: string, output: any = {}) => {
    const stack: Middleware[] = []
    installS3KeyPrefix({middlewareStack: {add: (middleware: Middleware) => stack.push(middleware)}}, prefix)
    let seen: any
    const send = async (input: any) => {
        const handler = stack[0](async args => {
            seen = args.input
            return {output: structuredClone(output)}
        }, {commandName})
        return handler({input})
    }
    return {stack, send, seen: () => seen}
}

test('an empty prefix installs nothing', () => {
    const {stack} = setup('', 'GetObjectCommand')
    assert.equal(stack.length, 0)
})

test('Key, Prefix, StartAfter and Delete.Objects are prefixed on a copy', async () => {
    const {send, seen} = setup('shared/lumi/', 'DeleteObjectsCommand')
    const input = {
        Bucket: 'b', Key: 'a/b.png', Prefix: 'a/', StartAfter: 'a/0',
        Delete: {Objects: [{Key: 'a/1'}, {Key: 'a/2'}]}
    }
    const snapshot = structuredClone(input)
    await send(input)
    assert.deepEqual(input, snapshot, 'the caller\'s params stay as they were (Content-MD5 is computed from them)')
    assert.equal(seen().Key, 'shared/lumi/a/b.png')
    assert.equal(seen().Prefix, 'shared/lumi/a/')
    assert.equal(seen().StartAfter, 'shared/lumi/a/0')
    assert.deepEqual(seen().Delete.Objects, [{Key: 'shared/lumi/a/1'}, {Key: 'shared/lumi/a/2'}])
    assert.equal(seen().Bucket, 'b')
})

test('a list is confined to the prefix and its keys come back stripped', async () => {
    const {send, seen} = setup('p/', 'ListObjectsV2Command', {
        Prefix: 'p/id/',
        Contents: [{Key: 'p/id/a.png', Size: 1}, {Key: 'p/id/b/c.png', Size: 2}],
        CommonPrefixes: [{Prefix: 'p/id/b/'}]
    })
    const result = await send({Bucket: 'b', Prefix: 'id/'})
    assert.equal(seen().Prefix, 'p/id/')
    assert.deepEqual(result.output.Contents.map((c: any) => c.Key), ['id/a.png', 'id/b/c.png'])
    assert.equal(result.output.Contents[0].Size, 1)
    assert.equal(result.output.Prefix, 'id/')
    assert.deepEqual(result.output.CommonPrefixes, [{Prefix: 'id/b/'}])

    await send({Bucket: 'b'})
    assert.equal(seen().Prefix, 'p/', 'a list without Prefix must not see the rest of the bucket')
})

test('other commands do not touch results', async () => {
    const {send} = setup('p/', 'GetObjectCommand', {Contents: [{Key: 'p/x'}]})
    const result = await send({Key: 'x'})
    assert.equal(result.output.Contents[0].Key, 'p/x')
})

test('CopySource throws instead of writing outside the prefix', async () => {
    const {send} = setup('p/', 'CopyObjectCommand')
    await assert.rejects(send({Key: 'a', CopySource: 'b/c'}), /CopySource/)
})
