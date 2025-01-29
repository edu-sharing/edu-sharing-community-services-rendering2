import * as H5P from '@lumieducation/h5p-server';
import * as dbImplementations from '@lumieducation/h5p-mongos3';
import {caching} from "cache-manager";
import {Db} from "@lumieducation/h5p-mongos3/node_modules/mongodb"
import {LaissezFairePermissionSystem, Logger} from "@lumieducation/h5p-server";

const log = new Logger("CreateH5PEditor")

/**
 * Create a H5PEditor object.
 *
 * @param config the configuration object
 * @param mongoDb
 * @param translationCallback a function that is called to retrieve translations of keys in a certain language; the keys use the i18next format (e.g. namespace:key).
 * @returns a H5PEditor object
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
    const s3 = dbImplementations.initS3({
        s3ForcePathStyle: true,
        signatureVersion: 'v4'
    })
    log.info("Initiated S3 client.")

    // Init library storage. We use mongo library storage.
    const libraryCollection = mongoDb.collection(process.env.LIBRARY_MONGO_COLLECTION)
    const libraryStorageOptions = {
        s3Bucket: process.env.LIBRARY_AWS_S3_BUCKET,
        maxKeyLength: Number.parseInt(process.env.AWS_S3_MAX_FILE_LENGTH, 10)
    }
    const mongoS3LibraryStorage = new dbImplementations.MongoS3LibraryStorage(
        s3,
        libraryCollection,
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

    const contentCollection = mongoDb.collection(process.env.CONTENT_MONGO_COLLECTION)
    const contentStorageOptions = {
        s3Bucket: process.env.CONTENT_AWS_S3_BUCKET,
        maxKeyLength: Number.parseInt(process.env.AWS_S3_MAX_FILE_LENGTH, 10)
    }
    const contentStorage = new dbImplementations.MongoS3ContentStorage(
        s3,
        contentCollection,
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
