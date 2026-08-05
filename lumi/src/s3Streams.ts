import { AsyncLocalStorage } from 'async_hooks';
import { NextFunction, Request, Response } from 'express';
import { Readable, Transform, TransformCallback } from 'stream';
import { Logger } from '@lumieducation/h5p-server';

const log = new Logger('S3Streams');

/**
 * Every content, library and temporary file the H5P player requests is served by
 * `@lumieducation/h5p-express` as `s3.getObject(...).Body.pipe(response)`. The body of an AWS SDK v3
 * `GetObject` is the raw `IncomingMessage`, so it keeps a socket of the S3 client's connection pool
 * (`maxSockets`, 50 by default) checked out until it is either read to the end or destroyed.
 *
 * `readable.pipe(response)` only *unpipes* the source when the response is destroyed early — it never
 * destroys it. A client that goes away mid-download (iframe reload, navigation, video seek, a request
 * the browser cancels) therefore leaks one pool socket per aborted request, permanently. Once 50 have
 * leaked, every further S3 request queues forever and Lumi stops serving H5P content while still
 * answering the Mongo-only routes — the
 * `@smithy/node-http-handler:WARN - socket usage at capacity=50 and N additional requests are enqueued`
 * failure mode.
 *
 * This module closes that gap: {@link fileStreamScopeMiddleware} opens a scope per GET request,
 * {@link guardFileStreams} registers every stream a storage hands out in the active scope, and when the
 * response closes all streams still open are destroyed, returning their sockets to the pool.
 */
interface StreamScope {
    streams: Set<Readable>;
}

const scopeStore = new AsyncLocalStorage<StreamScope>();

/**
 * Express middleware that binds the lifetime of the S3 streams opened while handling a **GET** request
 * to that request's response.
 *
 * Only GET requests get a scope: package uploads (`POST /edusharing`) install libraries by reading from
 * temporary storage, and that install deliberately outlives an aborted request — killing its streams
 * would abort the import and, since `updateLibrary` deletes a library on any error, corrupt the library
 * store. GET requests are the ones that stream S3 objects straight to the client, so they are both the
 * leak source and safe to clean up.
 */
export function fileStreamScopeMiddleware(req: Request, res: Response, next: NextFunction): void {
    if (req.method !== 'GET') {
        next();
        return;
    }

    const scope: StreamScope = { streams: new Set() };
    res.on('close', () => {
        for (const stream of scope.streams) {
            if (!stream.destroyed) {
                log.debug(`Response closed before its file stream ended - destroying it to free the S3 socket.`);
                stream.destroy();
            }
        }
        scope.streams.clear();
    });

    scopeStore.run(scope, () => next());
}

/**
 * Registers a stream in the current request scope so it is destroyed if the response closes before the
 * stream is done. Streams that finish on their own deregister themselves — a fully read body returns
 * its socket to the pool by itself. Outside a scope (startup, uploads, background work) this is a no-op.
 */
function trackFileStream<T extends Readable>(stream: T): T {
    const scope = scopeStore.getStore();
    if (!scope) {
        return stream;
    }

    scope.streams.add(stream);
    const forget = (): void => {
        scope.streams.delete(stream);
    };
    stream.once('end', forget);
    stream.once('close', forget);
    stream.once('error', forget);
    return stream;
}

/**
 * Returns a stream that emits at most the first `byteLimit` bytes of `source` and then stops the S3
 * download instead of draining the rest of the object.
 */
function limitStream(source: Readable, byteLimit: number): Readable {
    let remaining = byteLimit;
    const limited = new Transform({
        transform(chunk: Buffer, _encoding: BufferEncoding, callback: TransformCallback): void {
            if (remaining <= 0) {
                callback();
                return;
            }
            const slice = chunk.length <= remaining ? chunk : chunk.subarray(0, remaining);
            remaining -= slice.length;
            if (remaining <= 0) {
                source.unpipe(limited);
                source.destroy();
            }
            this.push(slice);
            if (remaining <= 0) {
                this.push(null);
            }
            callback();
        }
    });
    source.on('error', (error) => limited.destroy(error));
    source.pipe(limited);
    return limited;
}

interface FileStreamStorage {
    getFileStream(...args: any[]): Promise<Readable>;
}

/**
 * Wraps a storage's `getFileStream` so that
 *
 * 1. every stream it returns is bound to the current request (see {@link fileStreamScopeMiddleware}), and
 * 2. a range starting at byte 0 is honoured.
 *
 * (2) works around a bug in `@lumieducation/h5p-mongos3`: its storages only set the S3 `Range` header
 * when `rangeStart && rangeEnd` is truthy, so a `Range: bytes=0-1` request (Safari and iOS open every
 * video with exactly that probe) falls through to a full `GetObject`. `h5p-express` then answers 206
 * with `Content-Length: 2` and pipes the whole object into it, Node aborts the response with
 * `ERR_HTTP_CONTENT_LENGTH_MISMATCH`, and the untouched rest of the body pins its socket — one leaked
 * socket per media probe. Truncating the stream to the requested length keeps the response consistent
 * with the declared 206 headers and closes the S3 connection as soon as those bytes are through.
 *
 * @param storage the storage instance to patch in place
 * @param rangeStartIndex index of the `rangeStart` argument in the storage's `getFileStream` signature
 *                        (`rangeEnd` is expected right after it); omit for storages without ranges
 */
export function guardFileStreams<T extends FileStreamStorage>(storage: T, rangeStartIndex?: number): T {
    const original = storage.getFileStream.bind(storage);

    storage.getFileStream = async (...args: any[]): Promise<Readable> => {
        const stream = trackFileStream(await original(...args));
        if (rangeStartIndex === undefined) {
            return stream;
        }

        const rangeStart = args[rangeStartIndex];
        const rangeEnd = args[rangeStartIndex + 1];
        // Only the case the storage silently drops: a range that starts at 0. Anything else either
        // carries a proper Range header or is not a range request at all.
        if (rangeStart === 0 && typeof rangeEnd === 'number') {
            return trackFileStream(limitStream(stream, rangeEnd + 1));
        }
        return stream;
    };

    return storage;
}
