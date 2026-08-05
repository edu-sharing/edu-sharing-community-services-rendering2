import * as H5P from '@lumieducation/h5p-server';
import * as dbImplementations from '@lumieducation/h5p-mongos3';
import {caching} from "cache-manager";
import {Db} from "@lumieducation/h5p-mongos3/node_modules/mongodb"
import {LaissezFairePermissionSystem, Logger} from "@lumieducation/h5p-server";
import * as http from "http";
import * as https from "https";
import {guardFileStreams} from "./s3Streams";

const log = new Logger("CreateH5PEditor")

/**
 * Creates and initializes an H5P Editor instance using the provided configurations
 * and dependencies, such as database connections and caching mechanisms.
 *
 * @param {H5P.IH5PConfig} config - The H5P configuration object containing system-wide
 * settings and other mandatory configurations for the editor.
 *
 * @param {Db} mongoDb - A MongoDB database instance used for content and library storage,
 * required for the editor's functioning.
 *
 * @param {H5P.ITranslationFunction} [translationCallback] - An optional function for
 * handling translations in the editor, enabling localization of the editor's interface.
 *
 * @return {Promise<H5P.H5PEditor>} A promise that resolves with the initialized H5P editor
 * instance, which can be used for creating or editing H5P content.
 */
export default async function createH5PEditor(
    config: H5P.IH5PConfig,
    mongoDb: Db,
    translationCallback?: H5P.ITranslationFunction,
): Promise<H5P.H5PEditor> {
    log.info("Starting H5P editor creation.")

    // Alternative: Redis
    let cache = caching({
        store: 'memory',
        ttl: 60 * 60 * 24,
        max: 2 ** 10
    });
    log.info("Initiated in-memory cache.")

    // Alternative: Redis lock (needed for cluster mode and only for editor)
    let lock = new H5P.SimpleLockProvider();

    // Init S3 once
    const trustAllCertificates =
        process.env.AWS_S3_TRUST_ALL_CERTIFICATES === 'true';

    const region = process.env.AWS_S3_REGION || 'eu-central-1';

    const checksumCalculationWhenRequired =
        process.env.AWS_S3_CHECKSUM_CALCULATION_WHEN_REQUIRED === 'true';

    // Every file of every H5P page (all library JS/CSS plus the content files) is a separate S3 request,
    // and each one occupies a pool socket for as long as it streams. The AWS SDK default of 50 sockets is
    // a hard ceiling on that fan-out: once it is reached, requests queue up
    // ("socket usage at capacity=50 and N additional requests are enqueued") and the player stalls.
    const maxSockets = Number.parseInt(process.env.AWS_S3_MAX_SOCKETS || '256', 10);
    // 0 = disabled, and keep it that way unless you have measured otherwise. Despite the name this is not
    // a TCP-connect timeout: @smithy/node-http-handler starts the timer when the request is *created* and
    // only clears it once a socket has been assigned and connected, so it also bounds the time a request
    // waits for a free socket in the pool. Importing one H5P package fans out over every file of every
    // library it contains (unbounded `Promise.all`, easily >1500 queued uploads), and any value short of
    // that queue's drain time kills the queued uploads - on which LibraryManager deletes the libraries it
    // was copying and the whole import fails.
    const connectionTimeout = Number.parseInt(process.env.AWS_S3_CONNECTION_TIMEOUT_MS || '0', 10);
    // Socket *inactivity* timeout, 0 = disabled (the SDK default). Leave it off unless you know your
    // clients are fast: piping to a slow client applies backpressure, which stalls reads from the S3
    // socket and would trip this timeout mid-download. Leaked sockets are handled by s3Streams instead.
    const requestTimeout = Number.parseInt(process.env.AWS_S3_REQUEST_TIMEOUT_MS || '0', 10);

    const agentOptions = {keepAlive: true, maxSockets: maxSockets};

    const s3 = dbImplementations.initS3({
        forcePathStyle: true,
        region: region,
        ...(checksumCalculationWhenRequired
            ? {
                requestChecksumCalculation: "WHEN_REQUIRED",
                responseChecksumValidation: "WHEN_REQUIRED"
            }
            : {}),
        // Passed as plain NodeHttpHandler options (not an instance) so the client builds the handler with
        // the @smithy/node-http-handler version it depends on itself.
        requestHandler: {
            connectionTimeout: connectionTimeout,
            requestTimeout: requestTimeout,
            httpAgent: new http.Agent(agentOptions),
            httpsAgent: new https.Agent({
                ...agentOptions,
                ...(trustAllCertificates ? {rejectUnauthorized: false} : {})
            })
        }
    })
    log.info(`Initiated S3 client (maxSockets=${maxSockets}, connectionTimeout=${connectionTimeout}ms, requestTimeout=${requestTimeout}ms).`)

    const ensureBucketExists = async (bucketName: string): Promise<void> => {
        try {
            await s3.headBucket({ Bucket: bucketName });
            log.info(`Bucket ${bucketName} already exists.`);
        } catch (error: any) {
            if (error.$metadata?.httpStatusCode === 404 || error.name === 'NotFound') {
                log.info(`Bucket ${bucketName} not found. Creating...`);
                try {
                    await s3.createBucket({ Bucket: bucketName });
                    log.info(`Bucket ${bucketName} did not exist and was created successfully.`);
                } catch (createError: any) {
                    if (createError.name !== 'BucketAlreadyOwnedByYou' && createError.name !== 'BucketAlreadyExists') {
                        throw createError;
                    }
                }
            } else {
                log.error(`Error checking bucket ${bucketName}:`, error);
                throw error;
            }
        }
    };

    await Promise.all([
        ensureBucketExists(process.env.LIBRARY_AWS_S3_BUCKET),
        ensureBucketExists(process.env.CONTENT_AWS_S3_BUCKET),
        ensureBucketExists(process.env.TEMPORARY_AWS_S3_BUCKET)
    ]);


    // Init library storage. We use mongo library storage.
    const libraryStorageOptions = {
        s3Bucket: process.env.LIBRARY_AWS_S3_BUCKET,
        maxKeyLength: Number.parseInt(process.env.AWS_S3_MAX_FILE_LENGTH, 10)
    }
    // guardFileStreams: free the S3 socket when a client aborts a download - see s3Streams.ts.
    // The index names the position of `rangeStart` in the storage's getFileStream signature
    // (library files are never range-requested, hence none).
    const mongoS3LibraryStorage = guardFileStreams(new dbImplementations.MongoS3LibraryStorage(
        s3,
        mongoDb.collection(process.env.LIBRARY_MONGO_COLLECTION),
        libraryStorageOptions
    ));
    await mongoS3LibraryStorage.createIndexes();
    log.info("Initiated mongoDB library storage.")

    // Instantiate H5PEditor and all the stuff it needs
    const editorCache = new H5P.cacheImplementations.CachedKeyValueStorage(
        'kvcache',
        cache
    )
    const libraryCache = new H5P.cacheImplementations.CachedLibraryStorage(
        mongoS3LibraryStorage,
        cache
    )

    log.info("Initiated caches.")

    const contentStorageOptions = {
        s3Bucket: process.env.CONTENT_AWS_S3_BUCKET,
        maxKeyLength: Number.parseInt(process.env.AWS_S3_MAX_FILE_LENGTH, 10)
    }
    // getFileStream(contentId, filename, user, rangeStart, rangeEnd)
    const contentStorage = guardFileStreams(new dbImplementations.MongoS3ContentStorage(
        s3,
        mongoDb.collection(process.env.CONTENT_MONGO_COLLECTION),
        contentStorageOptions
    ), 3)

    log.info("Initiated S3 and mongo content storage.")
    const tempFileStorageOptions = {
        s3Bucket: process.env.TEMPORARY_AWS_S3_BUCKET,
        maxKeyLength: Number.parseInt(process.env.AWS_S3_MAX_FILE_LENGTH, 10)
    }
    // getFileStream(filename, user, rangeStart, rangeEnd)
    const tempFileStorage = guardFileStreams(new dbImplementations.S3TemporaryFileStorage(
        s3,
        tempFileStorageOptions
    ), 2)
    log.info("Initiated S3 temp file storage.")

    const editorOptions = {
        enableHubLocalization: true,
        enableLibraryNameLocalization: true,
        lockProvider: lock,
        permissionSystem: new LaissezFairePermissionSystem()
    }
    const h5pEditor = new H5P.H5PEditor(
        editorCache,
        config,
        libraryCache,
        contentStorage,
        tempFileStorage,
        translationCallback,
        undefined,
        editorOptions,
        undefined
    );

    // Set bucket lifecycle configuration for S3 temporary storage to make
    // sure temporary files expire.
    if (h5pEditor.temporaryStorage instanceof dbImplementations.S3TemporaryFileStorage) {
        await h5pEditor.temporaryStorage.setBucketLifecycleConfiguration(h5pEditor.config);
    }

    log.info("Initiated H5P editor.")
    return h5pEditor;
}
