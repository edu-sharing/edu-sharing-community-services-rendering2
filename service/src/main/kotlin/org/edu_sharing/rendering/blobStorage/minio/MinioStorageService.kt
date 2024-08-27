package org.edu_sharing.rendering.blobStorage.minio

import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.MongoException
import io.minio.*
import io.minio.errors.ErrorResponseException
import io.minio.messages.DeleteObject
import org.apache.catalina.util.URLEncoder
import org.apache.commons.codec.binary.Base64
import org.edu_sharing.rendering.blobStorage.StaticStorageService
import org.edu_sharing.rendering.blobStorage.StorageInfo
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.blobStorage.TrackingService
import org.edu_sharing.rendering.blobStorage.minio.bucket.BucketStrategy
import org.edu_sharing.rendering.config.MinioAdminClientProvider
import org.edu_sharing.rendering.controller.external.AssetController.Companion.ROOT_REQUEST_PATH
import org.edu_sharing.rendering.controller.external.AssetController.Companion.STATIC_ASSET_PATH
import org.edu_sharing.rendering.dto.AssetLinkParams
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.CachedObjectDetails
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.entity.TrackingEntry
import org.edu_sharing.rendering.exception.ResourceNotFoundException
import org.edu_sharing.rendering.repository.mongo.TrackingEntryRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.io.InputStream

@Service
class MinioStorageService(
    private val eduMinioClient: MinioClient,
    private val eduMinioAdminClient: MinioAdminClientProvider,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val bucketStrategy: BucketStrategy,
    private val trackingService: TrackingService
) : StorageService, StaticStorageService {

    val defaultChunkSize = 10485760L

    @Value("\${app.public.url}")
    lateinit var publicUrl: String

    @Value("\${app.public.port}")
    lateinit var port: String

    private val log = LoggerFactory.getLogger(javaClass)

    override fun putObject(cacheObject: CacheObject, inputStream: InputStream, metadata: Map<String, String>) {
        putObjectInternal(cacheObject, inputStream, bucketStrategy.getStoragePath(cacheObject), metadata)
    }

    override fun putObject(
        cacheObject: CacheObject,
        inputStream: InputStream,
        targetPath: String,
        metadata: Map<String, String>
    ) {
        putObjectInternal(cacheObject, inputStream, bucketStrategy.prefixStaticPath(cacheObject, targetPath), metadata)
    }

    private fun putObjectInternal(
        cacheObject: CacheObject,
        inputStream: InputStream,
        targetPath: String,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val bucket = bucketStrategy.getBucket(cacheObject)
        createBucket(bucket)
        val args = PutObjectArgs.builder()
            .bucket(bucket)
            .`object`(targetPath)
            .stream(inputStream, cacheObject.size, if (cacheObject.size < 0) defaultChunkSize else -1)
            .userMetadata(metadata)
        if (cacheObject.mimeType.isNotBlank()) {
            args.contentType(cacheObject.mimeType)
        }
        eduMinioClient.putObject(args.build())
        trackingService.trackCacheObject(cacheObject, bucket)
    }

    override fun getObjectLink(cacheObject: CacheObject): ObjectLink {
        val params = AssetLinkParams(
            nodeId = cacheObject.nodeId,
            hash = cacheObject.hash,
            quality = cacheObject.quality ?: 0,
            type = cacheObject.type,
            mimeType = cacheObject.mimeType
        )
        val mapper = ObjectMapper()
        val base = Base64().encode(mapper.writeValueAsString(params).toByteArray())
        val url = UriComponentsBuilder.newInstance()
            .scheme(publicUrl.substringBefore("://"))
            .host(publicUrl.substringAfter("://"))
            .port(port)
            .path("/public/asset")
            .queryParam("assetParams", URLEncoder().encode(base.decodeToString(), Charsets.UTF_8))
            .build()
            .toUriString()
        val objectLink = ObjectLink(link = url)
        try {
            val metadata = getStatObject(cacheObject).userMetadata()
            if (metadata.containsKey("width")) {
                objectLink.width = metadata["width"]?.toIntOrNull() ?: 0
            }
            if (metadata.containsKey("height")) {
                objectLink.height = metadata["height"]?.toIntOrNull() ?: 0
            }
            if (metadata.containsKey("isHighestResolution") && metadata["isHighestResolution"].toBoolean()) {
                objectLink.isHighestQuality = true
            }
        } catch (errorException: ErrorResponseException) {
            throw ResourceNotFoundException("Resource invalid or not yet cached.")
        }
        return objectLink
    }

    override fun getObjectLink(cacheObject: CacheObject, path: String): ObjectLink {

        val url = UriComponentsBuilder.newInstance()
            .scheme(publicUrl.substringBefore("://"))
            .host(publicUrl.substringAfter("://"))
            .port(port)
            .path("${ROOT_REQUEST_PATH}${STATIC_ASSET_PATH}${bucketStrategy.prefixStaticPath(cacheObject, path)}")
            .build()
            .toUriString()
        return ObjectLink(link = url)
    }

    override fun removeObject(cacheObject: CacheObject, isTemp: Boolean) {
        if (isTemp) {
            eduMinioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket("temp")
                    .`object`(getTempPath(cacheObject))
                    .build()
            )
        } else {
            val bucket = bucketStrategy.getBucket(cacheObject)
            val storagePath = bucketStrategy.getStoragePath(cacheObject)

            eduMinioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(storagePath)
                    .build()
            )

            trackingService.deleteTrackedObject(cacheObject, bucket)
        }
    }

    override fun getObjectStream(cacheObject: CacheObject, isTemp: Boolean): InputStream {
        if (isTemp) {
            return eduMinioClient.getObject(
                GetObjectArgs.Builder()
                    .bucket("temp")
                    .`object`(getTempPath(cacheObject))
                    .build()
            )
        } else {
            val bucket = bucketStrategy.getBucket(cacheObject)
            val path = bucketStrategy.getStoragePath(cacheObject)

            val response = eduMinioClient.getObject(
                GetObjectArgs.Builder()
                    .bucket(bucket)
                    .`object`(path)
                    .build()
            )
            trackingService.trackCacheObject(cacheObject, bucket)
            return response
        }
    }

    /**
     * Method for static interface
     */
    override fun getObjectStream(cacheObject: CacheObject, path: String): InputStream {
        val bucket = bucketStrategy.getBucket(cacheObject)
        val response = eduMinioClient.getObject(
            GetObjectArgs.Builder()
                .bucket(bucket)
                .`object`(bucketStrategy.getStoragePath(cacheObject))
                .build()
        )
        trackingService.trackCacheObject(cacheObject, bucket)
        return response
    }

    override fun getObjectChunkStream(
        cacheObject: CacheObject,
        length: Long,
        offset: Long,
        isTemp: Boolean
    ): InputStream {
        if (isTemp) {
            return eduMinioClient.getObject(
                GetObjectArgs.Builder()
                    .bucket("temp")
                    .`object`(getTempPath(cacheObject))
                    .offset(offset)
                    .length(length)
                    .build()
            )
        } else {
            val bucket = bucketStrategy.getBucket(cacheObject)
            val path = bucketStrategy.getStoragePath(cacheObject)

            val response = eduMinioClient.getObject(
                GetObjectArgs.Builder()
                    .bucket(bucket)
                    .`object`(path)
                    .offset(offset)
                    .length(length)
                    .build()
            )
            trackingService.trackCacheObject(cacheObject, bucket)
            return response
        }
    }

    /**
     * Method for static interface
     */
    override fun getObjectChunkStream(cacheObject: CacheObject, path: String, offset: Long, length: Long): InputStream {
        val storagePath = bucketStrategy.getStoragePath(cacheObject)
        val bucket = bucketStrategy.getBucket(cacheObject)

        val response = eduMinioClient.getObject(
            GetObjectArgs.Builder()
                .bucket(bucket)
                .`object`(storagePath)
                .offset(offset)
                .length(length)
                .build()
        )
        trackingService.trackCacheObject(cacheObject, bucket)
        return response
    }

    override fun putTempFile(cacheObject: CacheObject, inputStream: InputStream) {
        createBucket("temp")
        eduMinioClient.putObject(
            PutObjectArgs.builder()
                .bucket("temp")
                .`object`(this.getTempPath(cacheObject))
                .stream(inputStream, cacheObject.size, if (cacheObject.size < 0) defaultChunkSize else -1)
                .contentType(cacheObject.mimeType)
                .build()
        )
    }

    override fun getFileProperties(cacheObject: CacheObject): CachedObjectDetails {
        try {
            val statObject = getStatObject(cacheObject)
            return CachedObjectDetails(
                size = statObject.size(),
                mimeType = statObject.contentType()
            )
        } catch (exception: Exception) {
            log.error(exception.toString())
            throw ResourceNotFoundException("File properties for cached object not found.")
        }
    }

    override fun getFileProperties(cacheObject: CacheObject, path: String): CachedObjectDetails {
        try {
            val statObject = getStatObject(cacheObject, path)
            return CachedObjectDetails(
                size = statObject.size(),
                mimeType = statObject.contentType()
            )
        } catch (exception: Exception) {
            log.error(exception.toString())
            throw ResourceNotFoundException("File properties for cached object not found.")
        }
    }

    override fun getStorageInfo(): List<StorageInfo> {
        val usageInfo = eduMinioAdminClient.adminClient.dataUsageInfo
        val infoList = mutableListOf<StorageInfo>()
        usageInfo.bucketsUsageInfo().forEach { entry ->
            val quota = eduMinioAdminClient.adminClient.getBucketQuota(entry.key)
            val storageInfo = StorageInfo(
                location = entry.key,
                size = entry.value.size(),
                maxSize = quota
            )
            infoList.add(storageInfo)
        }
        return infoList
    }

    override fun freeStorage(storageInfo: StorageInfo, lowerThreshold: Float) {
        val maxSize = (lowerThreshold * storageInfo.maxSize).toLong()

        val page = 0
        var totalSize = 0L
        var result: Page<TrackingEntry>?
        val minioObjectsToDelete = mutableListOf<DeleteObject>()
        val trackingEntriesToDelete = mutableListOf<TrackingEntry>()
        do {
            result = trackingService.getOldestTrackedObjects(storageInfo.location)

            // TODO handle h5p caches in lumi
            for (entry in result) {
                if (totalSize >= maxSize) {
                    break
                }

                val storedObjectResults = eduMinioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(storageInfo.location)
                    .prefix(bucketStrategy.getCacheObjectRootPath(CacheObject.of(entry.repoId, entry.nodeId, entry.hash, entry.type)))
                    .build())

                totalSize += storedObjectResults.mapNotNull {  try { it.get() } catch (_:Exception) {null} }.sumOf { it.size() }
                minioObjectsToDelete.addAll(storedObjectResults.mapNotNull { try { it.get() } catch (_:Exception) {null} }.map { DeleteObject(it.objectName()) }.toList())
                trackingEntriesToDelete.add(entry)
            }
        } while (result?.hasNext() == true && totalSize < maxSize)


        val removeObjectResults = eduMinioClient.removeObjects(
            RemoveObjectsArgs.builder()
                .bucket(storageInfo.location)
                .objects(minioObjectsToDelete)
                .build()
        )

        for (removeObjectResult in removeObjectResults){
            val error = removeObjectResult.get()
            log.warn("Error deleting object " + error.objectName() + "; " + error.message())
        }
        trackingService.deleteAllTrackedObjects(trackingEntriesToDelete)
    }

    override fun objectExists(cacheObject: CacheObject): Boolean {
        try {
            getStatObject(cacheObject)
        } catch (_: Exception) {
            return false
        }
        return true
    }

    /**
     * Method for static interface
     */
    override fun objectExists(cacheObject: CacheObject, path: String): Boolean {
        try {
            getStatObject(cacheObject, path)
        } catch (_: Exception) {
            return false
        }
        return true
    }


    private fun getStatObject(cacheObject: CacheObject, path: String?=null): StatObjectResponse {
        val storagePath = if(path == null) bucketStrategy.getStoragePath(cacheObject) else bucketStrategy.prefixStaticPath(cacheObject, path)
        return eduMinioClient.statObject(
            StatObjectArgs.builder()
                .bucket(bucketStrategy.getBucket(cacheObject))
                .`object`(storagePath)
                .build()
        )
    }

    private fun createBucket(name: String) {
        if (eduMinioClient.bucketExists(BucketExistsArgs.builder().bucket(name).build())) {
            return
        }
        eduMinioClient.makeBucket(MakeBucketArgs.builder().bucket(name).build())
    }

    private fun getTempPath(cacheObject: CacheObject): String {
        return "${cacheObject.type}/${cacheObject.nodeId}/${cacheObject.hash}${bucketStrategy.getExtensionFromMimeType(cacheObject.mimeType)}"
    }

}
