package org.edu_sharing.rendering.storage

import com.fasterxml.jackson.databind.ObjectMapper
import org.edu_sharing.rendering.asset.AssetController.Companion.ROOT_REQUEST_PATH
import org.edu_sharing.rendering.asset.AssetController.Companion.STATIC_ASSET_PATH
import org.edu_sharing.rendering.asset.dto.AssetLinkParams
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.CachedObjectDetails
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.storage.bucket.BucketPerCustomerStrategy
import org.edu_sharing.rendering.storage.bucket.BucketStrategy
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.io.InputStream
import java.net.URLEncoder
import java.util.*

private const val TEMP_BUCKET = "temp"

@Service
class S3StorageService(
    private val s3Client: S3Client,
    private val trackingService: TrackingService,
    private val appInfo: AppInfo,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val bucketStrategy: BucketStrategy,
    private val repoRegistrationStorageService: RepositoryRegistrationStorageService,
    @param:Lazy private val storageManagerRegistry: StorageManagerRegistry
) : StorageService, StaticStorageService {
    override fun putObject(
        cacheObject: CacheObject,
        inputStream: InputStream,
        metadata: Map<String, String>
    ) {
        putObjectInternal(
            cacheObject = cacheObject,
            inputStream = inputStream,
            targetPath = bucketStrategy.getStoragePath(cacheObject),
            metadata = metadata
        )
    }

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getObjectLink(cacheObject: CacheObject): Pair<ObjectLink, Long> {
        val params = AssetLinkParams(
            repoId = cacheObject.repoId,
            nodeId = cacheObject.nodeId,
            hash = cacheObject.hash,
            quality = cacheObject.quality ?: 0,
            type = cacheObject.type,
            mimeType = cacheObject.mimeType
        )

        val base64Params = Base64.getEncoder().encodeToString(ObjectMapper().writeValueAsBytes(params))
        val url = UriComponentsBuilder.newInstance()
            .scheme(appInfo.public.protocol)
            .host(appInfo.public.host)
            .port(appInfo.public.port.toInt())
            .pathSegment(appInfo.public.path.trim('/'), "public/asset")
            .queryParam("assetParams", URLEncoder.encode(base64Params, Charsets.UTF_8))
            .build()
            .toUriString()

        val objectLink = ObjectLink(link = url)

        val head = try {
            getHeadObject(cacheObject)
        } catch (_: NoSuchKeyException) {
            throw ResourceNotFoundException("Resource invalid or not yet cached.")
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) throw ResourceNotFoundException("Resource invalid or not yet cached.") else throw e
        }

        val lastModified = head.lastModified().epochSecond

        val metadata = head.metadata()
        metadata["width"]?.let { objectLink.width = it.toIntOrNull() ?: 0 }
        metadata["height"]?.let { objectLink.height = it.toIntOrNull() ?: 0 }

        return Pair(objectLink, lastModified)
    }

    override fun removeTempObject(cacheObject: CacheObject) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(TEMP_BUCKET)
                .key(getTempPath(cacheObject))
                .build()
        )
    }

    private fun getBucket(isTemp: Boolean, cacheObject: CacheObject): String {
        return if (isTemp) TEMP_BUCKET else bucketStrategy.getBucket(cacheObject)
    }

    private fun getStoragePath(isTemp: Boolean, cacheObject: CacheObject): String {
        return if (isTemp) getTempPath(cacheObject) else bucketStrategy.getStoragePath(cacheObject)
    }

    override fun removeObjects(cacheObjects: List<CacheObject>, isTemp: Boolean) {
        val objectsByBucket = cacheObjects.groupBy { getBucket(isTemp, it) }

        objectsByBucket.forEach { (bucket, bucketCacheObjects) ->
            // For each bucket, find all actual keys matching the hash prefixes
            val allKeysToDelete = bucketCacheObjects.flatMap { cacheObject ->
                val prefix = bucketStrategy.getCacheObjectRootPath(cacheObject)
                s3Client.listObjectsV2Paginator(
                    ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .build()
                ).contents().map { it.key() }
            }

            // Perform bulk delete in chunks of 1000 (S3 limit)
            allKeysToDelete.chunked(1000).forEach { chunk ->
                s3Client.deleteObjects(
                    DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(
                            Delete.builder()
                                .objects(chunk.map { ObjectIdentifier.builder().key(it).build() })
                                .build()
                        )
                        .build()
                )
            }
        }
    }

    override fun getObjectStream(
        cacheObject: CacheObject,
        isTemp: Boolean
    ): InputStream {
        val bucket = getBucket(isTemp, cacheObject)
        val key = getStoragePath(isTemp, cacheObject)

        val stream = s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()
        )

        if (! isTemp) {
            trackingService.trackCacheObject(cacheObject, bucket)
        }
        return stream
    }

    override fun getObjectChunkStream(
        cacheObject: CacheObject,
        length: Long,
        offset: Long,
        isTemp: Boolean
    ): InputStream {
        val rangeHeader = "bytes=$offset-${offset + length - 1}"

        val bucket = getBucket(isTemp, cacheObject)
        val key = getStoragePath(isTemp, cacheObject)

        val stream = s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .range(rangeHeader)
                .build()
        )

        trackingService.trackCacheObject(cacheObject, bucket)
        return stream
    }

    override fun putTempFile(
        cacheObject: CacheObject,
        inputStream: InputStream
    ) {
        createBucketIfMissing(TEMP_BUCKET)
        val request = PutObjectRequest.builder()
            .bucket(TEMP_BUCKET)
            .key(getTempPath(cacheObject))
            .contentType(cacheObject.mimeType.ifBlank { "application/octet-stream" })
            .build()

        val body =
            if (cacheObject.size >= 0) {
                RequestBody.fromInputStream(inputStream, cacheObject.size)
            } else {
                // AWS SDK v2 sync client needs a known content-length for InputStream.
                // Fallback: buffer in-memory to determine length.
                val bytes = inputStream.readAllBytes()
                RequestBody.fromBytes(bytes)
            }

        s3Client.putObject(request, body)
    }

    override fun getFileProperties(cacheObject: CacheObject): CachedObjectDetails {
        try {
            val head = getHeadObject(cacheObject)
            return CachedObjectDetails(
                size = head.contentLength(),
                mimeType = head.contentType() ?: ""
            )
        } catch (exception: Exception) {
            log.error(exception.toString(), exception)
            throw ResourceNotFoundException("File properties for cached object not found.")
        }
    }

    override fun getStorageInfo(): List<StorageInfo> {
        val aggregation = trackingService.getBucketAggregation()

        return aggregation.map {
            StorageInfo(
                location = it.repoId,
                size = it.totalSize,
                maxSize = repoRegistrationStorageService.getRegistrationByRepoId(it.repoId).map { rep -> rep.quota }
                    .orElse(0),
            )
        }
    }

    override fun objectExists(cacheObject: CacheObject): Boolean {
        return try {
            getHeadObject(cacheObject)
            true
        } catch (_: NoSuchKeyException) {
            false
        } catch (e: S3Exception) {
            // Fallback: Some S3-compatible endpoints (or certain configs) may not throw NoSuchKeyException directly.
            if (e.statusCode() == 404) false else throw e
        }
    }

    override fun isStoringByRepoId(): Boolean {
        return bucketStrategy is BucketPerCustomerStrategy
    }

    override fun getDirectorySize(bucket: String, directory: String): Long {
        return s3Client.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(directory).build())
            .contents().sumOf { it.size() }
    }

    /**
     * Calculates the total used storage space for a given repository.
     *
     * Attention: This method can be quite expensive and should only be used for select admin tasks
     *
     * @param repoId The identifier of the repository for which the used space is to be calculated.
     * @return The total used storage space in bytes as a `Long`.
     */
    override fun getUsedSpace(repoId: String): Pair<Long, List<String>> {
        var managedBuckets = storageManagerRegistry.getStorageManagers().flatMap { it.getManagedBuckets() }
        if (isStoringByRepoId()) {
            managedBuckets = managedBuckets.filter { it.contains(repoId) }
        }

        var totalSize = 0L

        managedBuckets.forEach { bucket ->
            totalSize += s3Client.listObjectsV2Paginator(
                ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .build()
            ).contents().sumOf { it.size() }
        }

        return totalSize to managedBuckets
    }

    override fun getBuckets(): List<String> {
        return s3Client.listBuckets().buckets().map { it.name() }
    }

    /**
     * Method for static interface
     */
    override fun putObject(
        cacheObject: CacheObject,
        inputStream: InputStream,
        targetPath: String,
        metadata: Map<String, String>
    ) {
        putObjectInternal(
            cacheObject = cacheObject,
            inputStream = inputStream,
            targetPath = bucketStrategy.getStoragePath(cacheObject, targetPath),
            metadata = metadata
        )
    }

    /**
     * Method for static interface
     */
    override fun getObjectLink(
        cacheObject: CacheObject,
        path: String
    ): ObjectLink {
        val url = UriComponentsBuilder.newInstance()
            .scheme(appInfo.public.protocol)
            .host(appInfo.public.host)
            .port(appInfo.public.port.toInt())
            .path(
                "${appInfo.public.path.trim('/')}${ROOT_REQUEST_PATH}${STATIC_ASSET_PATH}${
                    bucketStrategy.prefixStaticPath(
                        cacheObject,
                        path
                    )
                }"
            )
            .build()
            .toUriString()
        return ObjectLink(link = url)
    }

    /**
     * Method for static interface
     */
    override fun getObjectStream(
        cacheObject: CacheObject,
        path: String
    ): InputStream {
        val bucket = bucketStrategy.getBucket(cacheObject)
        val key = bucketStrategy.getStoragePath(cacheObject, path)

        val stream = s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()
        )

        trackingService.trackCacheObject(cacheObject, bucket)
        return stream
    }

    /**
     * Method for static interface
     */
    override fun getObjectChunkStream(
        cacheObject: CacheObject,
        path: String,
        offset: Long,
        length: Long
    ): InputStream {
        val bucket = bucketStrategy.getBucket(cacheObject)
        val key = bucketStrategy.getStoragePath(cacheObject, path)
        val rangeHeader = "bytes=$offset-${offset + length - 1}"

        val stream = s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .range(rangeHeader)
                .build()
        )

        trackingService.trackCacheObject(cacheObject, bucket)
        return stream
    }

    /**
     * Method for static interface
     */
    override fun getFileProperties(
        cacheObject: CacheObject,
        path: String
    ): CachedObjectDetails {
        try {
            val head = getHeadObject(cacheObject, path)
            return CachedObjectDetails(
                size = head.contentLength(),
                mimeType = head.contentType() ?: ""
            )
        } catch (exception: Exception) {
            log.error(exception.toString(), exception)
            throw ResourceNotFoundException("File properties for cached object not found.")
        }
    }

    /**
     * Method for static interface
     */
    override fun objectExists(
        cacheObject: CacheObject,
        path: String
    ): Boolean {
        return try {
            getHeadObject(cacheObject, path)
            true
        } catch (_: NoSuchKeyException) {
            false
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) false else throw e
        }
    }

    /**
     * Method for static interface
     */
    override fun getCacheObjectFromStaticPath(path: String): Pair<CacheObject, String> {
        return bucketStrategy.getCacheObjectFromStaticPath(path)
    }

    /**
     * Method for static interface
     */
    override fun getStoragePath(
        cacheObject: CacheObject,
        path: String
    ): String {
        return bucketStrategy.getStoragePath(cacheObject, path)
    }

    private fun putObjectInternal(
        cacheObject: CacheObject,
        inputStream: InputStream,
        targetPath: String,
        metadata: Map<String, String>
    ) {
        val bucket = bucketStrategy.getBucket(cacheObject)
        createBucketIfMissing(bucket)

        val requestBuilder = PutObjectRequest.builder()
            .bucket(bucket)
            .key(targetPath)
            .metadata(metadata)

        if (cacheObject.mimeType.isNotBlank()) {
            requestBuilder.contentType(cacheObject.mimeType)
        }

        val request = requestBuilder.build()

        val body =
            if (cacheObject.size >= 0) {
                RequestBody.fromInputStream(inputStream, cacheObject.size)
            } else {
                // AWS SDK v2 sync client needs a known content-length for InputStream.
                // Fallback: buffer in-memory to determine length.
                val bytes = inputStream.readAllBytes()
                RequestBody.fromBytes(bytes)
            }

        s3Client.putObject(request, body)
        val size = getDirectorySize(bucket, bucketStrategy.getCacheObjectRootPath(cacheObject))
        trackingService.trackCacheObject(cacheObject, bucket, size)
    }

    private fun createBucketIfMissing(bucket: String) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build())
        } catch (_: NoSuchBucketException) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
        }
    }

    private fun getHeadObject(cacheObject: CacheObject, path: String? = null): HeadObjectResponse {
        val storagePath =
            if (path == null) bucketStrategy.getStoragePath(cacheObject)
            else bucketStrategy.getStoragePath(cacheObject, path)

        return s3Client.headObject(
            HeadObjectRequest.builder()
                .bucket(bucketStrategy.getBucket(cacheObject))
                .key(storagePath)
                .build()
        )
    }

    private fun getTempPath(cacheObject: CacheObject): String {
        return "${cacheObject.type}/${cacheObject.nodeId}/${cacheObject.hash}${
            bucketStrategy.getExtensionFromMimeType(cacheObject.mimeType)
        }"
    }
}
