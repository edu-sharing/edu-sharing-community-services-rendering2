import * as H5P from '@lumieducation/h5p-server';
import * as dbImplementations from '@lumieducation/h5p-mongos3';
import {caching} from "cache-manager";
import {Db} from "@lumieducation/h5p-mongos3/node_modules/mongodb"
import {LaissezFairePermissionSystem, Logger} from "@lumieducation/h5p-server";
import * as https from "https";
import { NodeHttpHandler } from "@smithy/node-http-handler";

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

    const s3 = dbImplementations.initS3({
        forcePathStyle: true,
        region: region,
        ...(trustAllCertificates
            ? {
                requestHandler: new NodeHttpHandler({
                    httpsAgent: new https.Agent({ rejectUnauthorized: false })
                })
            }
            : {})
    })
    log.info("Initiated S3 client.")

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
    const mongoS3LibraryStorage = new dbImplementations.MongoS3LibraryStorage(
        s3,
        mongoDb.collection(process.env.LIBRARY_MONGO_COLLECTION),
        libraryStorageOptions
    );
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
    const contentStorage = new dbImplementations.MongoS3ContentStorage(
        s3,
        mongoDb.collection(process.env.CONTENT_MONGO_COLLECTION),
        contentStorageOptions
    )

    log.info("Initiated S3 and mongo content storage.")
    const tempFileStorageOptions = {
        s3Bucket: process.env.TEMPORARY_AWS_S3_BUCKET,
        maxKeyLength: Number.parseInt(process.env.AWS_S3_MAX_FILE_LENGTH, 10)
    }
    const tempFileStorage = new dbImplementations.S3TemporaryFileStorage(
        s3,
        tempFileStorageOptions
    )
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
