import * as H5P from '@lumieducation/h5p-server';
import * as dbImplementations from '@lumieducation/h5p-mongos3';
import {caching} from "cache-manager";
import {Db} from "@lumieducation/h5p-mongos3/node_modules/mongodb"
import {LaissezFairePermissionSystem, Logger} from "@lumieducation/h5p-server";
import * as http from "http";
import * as https from "https";
import {guardFileStreams} from "./s3Streams";
import {registerS3PoolWatchdog, S3PoolWatchdog} from "./s3Pool";
import {installS3Middleware, Semaphore} from "./s3Throttle";
import {installS3KeyPrefix} from "./s3KeyPrefix";
import {planS3Setup, readS3Scopes, TemporaryStorageMode} from "./s3Scopes";
import {UnavailableTemporaryStorage} from "./importTemporaryStorage";

const log = new Logger("CreateH5PEditor")

/** Reads an integer >= 0 from the environment; anything else (unset, negative, not a number) is `fallback`. */
function readNonNegativeInt(name: string, fallback: number): number {
    const raw = process.env[name]
    const parsed = Number.parseInt(raw ?? '', 10)
    if (Number.isFinite(parsed) && parsed >= 0) {
        return parsed
    }
    if (raw) {
        log.warn(`Invalid ${name} "${raw}", using ${fallback}.`)
    }
    return fallback
}

const DEFAULT_TEMP_BUCKET_EXPIRATION_DAYS = 1

/**
 * Applies an expiration rule (days from `TEMPORARY_AWS_S3_BUCKET_EXPIRATION_DAYS`, default 1) to
 * the temporary bucket. An existing matching rule is left alone; any other rule set is replaced, so
 * a changed value takes effect on the next start. Failures are logged, never fatal.
 */
async function ensureTempBucketExpiration(
    s3: ReturnType<typeof dbImplementations.initS3>, bucket: string): Promise<void> {
    const raw = process.env.TEMPORARY_AWS_S3_BUCKET_EXPIRATION_DAYS
    const parsed = Number.parseInt(raw ?? '', 10)
    const days = Number.isFinite(parsed) && parsed >= 1 ? parsed : DEFAULT_TEMP_BUCKET_EXPIRATION_DAYS
    if (raw && days !== parsed) {
        log.warn(`Invalid TEMPORARY_AWS_S3_BUCKET_EXPIRATION_DAYS "${raw}", using ${days} day(s).`)
    }
    try {
        const current = await s3.getBucketLifecycleConfiguration({Bucket: bucket}).catch(() => undefined)
        if (current?.Rules?.some(rule =>
            rule.Status === 'Enabled' && rule.Expiration?.Days === days
            && (rule.Filter?.Prefix ?? rule.Prefix ?? '') === '')) {
            log.info(`Bucket ${bucket} already expires objects after ${days} day(s).`)
            return
        }
        await s3.putBucketLifecycleConfiguration({
            Bucket: bucket,
            LifecycleConfiguration: {
                Rules: [{
                    ID: 'expire-temporary-files',
                    Filter: {Prefix: ''},
                    Status: 'Enabled',
                    Expiration: {Days: days}
                }]
            }
        })
        log.info(`Set expiration of bucket ${bucket} to ${days} day(s).`)
    } catch (error: any) {
        log.error(`Could not set lifecycle configuration of bucket ${bucket}: ${error.message}`)
    }
}

/**
 * The H5P editor plus the storages and settings it was built from.
 *
 * An import into a per-package library cache needs its own `H5PEditor` - one whose library storage
 * is the package's, not the global one - but it must write content and temporary files into exactly
 * the same places as the main editor. Handing these pieces out keeps that second editor a mirror of
 * this one instead of a second, drifting copy of the S3/Mongo wiring.
 */
export interface H5PDeps {
    /** The shared editor over the global library storage. */
    editor: H5P.H5PEditor
    config: H5P.IH5PConfig
    editorCache: H5P.IKeyValueStorage
    contentStorage: H5P.IContentStorage
    /**
     * The shared temp storage. In `disk` mode it is an {@link UnavailableTemporaryStorage}: temp files
     * then only exist inside an import, see `withImportTemporaryStorage`.
     */
    temporaryStorage: H5P.ITemporaryFileStorage
    temporaryStorageMode: TemporaryStorageMode
    translationCallback?: H5P.ITranslationFunction
    editorOptions: {
        enableHubLocalization: boolean
        enableLibraryNameLocalization: boolean
        lockProvider: H5P.ILockProvider
        permissionSystem: H5P.IPermissionSystem
    }
}

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
 * @return {Promise<H5PDeps>} A promise that resolves with the initialized H5P editor and the
 * storages and settings it was built from (see {@link H5PDeps}).
 */
export default async function createH5PEditor(
    config: H5P.IH5PConfig,
    mongoDb: Db,
    translationCallback?: H5P.ITranslationFunction,
): Promise<H5PDeps> {
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
    const httpAgent = new http.Agent(agentOptions)
    const httpsAgent = new https.Agent({
        ...agentOptions,
        ...(trustAllCertificates ? {rejectUnauthorized: false} : {})
    })

    const makeS3Client = () => dbImplementations.initS3({
        forcePathStyle: true,
        region: region,
        ...(checksumCalculationWhenRequired
            ? {
                requestChecksumCalculation: "WHEN_REQUIRED",
                responseChecksumValidation: "WHEN_REQUIRED"
            }
            : {}),
        // Passed as plain NodeHttpHandler options (not an instance) so the client builds the handler with
        // the @smithy/node-http-handler version it depends on itself. Every client gets the same agents,
        // so the pool limit, the watchdog and resetS3Pool stay global.
        requestHandler: {
            connectionTimeout: connectionTimeout,
            requestTimeout: requestTimeout,
            httpAgent,
            httpsAgent
        }
    })
    const s3 = makeS3Client()
    log.info(`Initiated S3 client (maxSockets=${maxSockets}, connectionTimeout=${connectionTimeout}ms, requestTimeout=${requestTimeout}ms).`)

    // Both timeouts above stay off, so a socket that is never given back (a body nobody reads, a peer
    // that stops answering) would hold its pool slot forever, and once all of them are gone every S3
    // request - player and import alike - queues for good. The watchdog notices a full pool in which
    // nothing settles any more and destroys its sockets; see s3Pool.ts. 0 disables it.
    const poolStallMs = readNonNegativeInt('AWS_S3_POOL_STALL_MS', 120_000)
    // How many S3 requests one package import may have in flight, so the rest of the pool stays
    // available to players; see s3Throttle.ts.
    const importConcurrency = Math.max(1, readNonNegativeInt('H5P_IMPORT_S3_CONCURRENCY', 64))
    const poolWatchdog = new S3PoolWatchdog([httpAgent, httpsAgent], {
        stallMs: poolStallMs,
        checkIntervalMs: Math.max(1_000, Math.min(15_000, Math.floor(poolStallMs / 4)))
    })
    // One semaphore for all clients: the limit is per import, not per client.
    const importSemaphore = new Semaphore(importConcurrency)
    installS3Middleware(s3, importSemaphore, poolWatchdog)
    // A storage under a key prefix needs a client of its own, because the prefix middleware sits on the
    // client. Without prefixes every storage uses `s3`, exactly as before. The bucket management and the
    // temp storage always use the plain client.
    const scopedClient = (prefix: string) => {
        if (!prefix) {
            return s3
        }
        const client = makeS3Client()
        installS3Middleware(client, importSemaphore, poolWatchdog)
        installS3KeyPrefix(client, prefix)
        return client
    }
    const scopes = readS3Scopes()
    const plan = planS3Setup(scopes)
    const contentS3 = scopedClient(plan.contentClientPrefix)
    const libraryS3 = scopedClient(plan.libraryClientPrefix)
    if (plan.contentClientPrefix || plan.libraryClientPrefix) {
        log.info(`S3 key prefixes: content "${plan.contentClientPrefix}", libraries "${plan.libraryClientPrefix}".`)
    }
    registerS3PoolWatchdog(poolWatchdog)
    poolWatchdog.start()
    log.info(`S3 pool watchdog ${poolStallMs ? `armed (stall after ${poolStallMs}ms)` : 'disabled'}, ` +
        `${importConcurrency} concurrent S3 requests per import.`)

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

    const diskTemp = plan.temporaryStorage === 'disk'
    // Both lists are empty or filled depending on bucket management; see planS3Setup.
    await Promise.all(plan.ensureBuckets.map(ensureBucketExists))
    // The operator owns these buckets (e.g. a single bucket provided by the hosting). Never create
    // one; only say so when it is not reachable.
    await Promise.all(plan.headOnlyBuckets.map(async bucket => {
        try {
            await s3.headBucket({Bucket: bucket})
        } catch (error: any) {
            log.warn(`Bucket ${bucket} is not accessible (AWS_S3_MANAGE_BUCKETS=false, not creating it): ${error.message}`)
        }
    }))
    if (diskTemp) {
        log.info("Temporary files go to local disk, one directory per import (H5P_TEMPORARY_STORAGE=disk).")
    }


    // Init library storage. We use mongo library storage.
    const libraryStorageOptions = {
        s3Bucket: process.env.LIBRARY_AWS_S3_BUCKET,
        maxKeyLength: Number.parseInt(process.env.AWS_S3_MAX_FILE_LENGTH, 10)
    }
    // guardFileStreams: free the S3 socket when a client aborts a download - see s3Streams.ts.
    // The index names the position of `rangeStart` in the storage's getFileStream signature
    // (library files are never range-requested, hence none).
    const mongoS3LibraryStorage = guardFileStreams(new dbImplementations.MongoS3LibraryStorage(
        libraryS3,
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
        contentS3,
        mongoDb.collection(process.env.CONTENT_MONGO_COLLECTION),
        contentStorageOptions
    ), 3)

    log.info("Initiated S3 and mongo content storage.")
    let tempFileStorage: H5P.ITemporaryFileStorage
    if (diskTemp) {
        tempFileStorage = new UnavailableTemporaryStorage()
    } else {
        const tempFileStorageOptions = {
            s3Bucket: process.env.TEMPORARY_AWS_S3_BUCKET,
            maxKeyLength: Number.parseInt(process.env.AWS_S3_MAX_FILE_LENGTH, 10)
        }
        // getFileStream(filename, user, rangeStart, rangeEnd)
        tempFileStorage = guardFileStreams(new dbImplementations.S3TemporaryFileStorage(
            s3,
            tempFileStorageOptions
        ), 2)
        log.info("Initiated S3 temp file storage.")
    }

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

    // Make sure temporary files expire. S3TemporaryFileStorage never deletes anything itself (its
    // listFiles() is empty, so TemporaryFileManager.cleanUp() is a no-op); expiry is the bucket's job.
    // We set the lifecycle rule ourselves instead of calling its setBucketLifecycleConfiguration:
    // that one divides temporaryFileLifetime by 1000*60*24 instead of ms-per-day (120 minutes
    // become 5 days) and cannot be steered in real days.
    // Skipped without bucket management (the bucket is not ours to configure) and with disk temp storage
    // (there is no temp bucket).
    if (plan.setTemporaryLifecycle) {
        await ensureTempBucketExpiration(s3, process.env.TEMPORARY_AWS_S3_BUCKET)
    }

    log.info("Initiated H5P editor.")
    return {
        editor: h5pEditor,
        config,
        editorCache,
        contentStorage,
        temporaryStorage: tempFileStorage,
        temporaryStorageMode: scopes.temporaryStorage,
        translationCallback,
        editorOptions
    };
}
