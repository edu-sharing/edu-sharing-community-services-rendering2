package org.edu_sharing.rendering.blobStorage

import io.minio.*
import io.minio.http.Method
import org.edu_sharing.rendering.dto.CacheObject
import org.springframework.stereotype.Service
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

@Service
class MinioService(private val eduMinioClient: MinioClient) : StorageService {
    override fun putObject(cacheObject: CacheObject, inputStream: InputStream) {
        createBucket(cacheObject.type)
        val metadata = mapOf(
            "hash" to cacheObject.hash,
            "size" to cacheObject.size.toString()
        )
        eduMinioClient.putObject(
            PutObjectArgs.builder().bucket(cacheObject.type).`object`(getStoragePath(cacheObject)).stream(
                inputStream, cacheObject.size, -1).userMetadata(metadata).contentType("image/jpeg").build()
        )
    }

    override fun getObjectLink(cacheObject: CacheObject): String {
        val url = eduMinioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(cacheObject.type)
                .`object`(getStoragePath(cacheObject))
                .expiry(1, TimeUnit.HOURS)
                .build()
        )
        return url
    }

    override fun removeObject(cacheObject: CacheObject) {
        eduMinioClient.removeObject(RemoveObjectArgs.builder().bucket(cacheObject.type)
            .`object`(getStoragePath(cacheObject)).build())
    }

    override fun getObjectStream(cacheObject: CacheObject, isTemp: Boolean): GetObjectResponse {
        return eduMinioClient.getObject(
            GetObjectArgs.Builder()
                .bucket(if (isTemp) "temp" else cacheObject.type)
                .`object`(if (isTemp) getTempPath(cacheObject) else getStoragePath(cacheObject))
                .build()
        )
    }

    override fun putTempFile(cacheObject: CacheObject, inputStream: FileInputStream) {
        createBucket("temp")
        eduMinioClient.putObject(
            PutObjectArgs.builder().bucket("temp").`object`(this.getTempPath(cacheObject)).stream(
                inputStream, cacheObject.size, -1).contentType(cacheObject.mimeType).build()
        )
    }

    private fun createBucket(name: String) {
        if (eduMinioClient.bucketExists(BucketExistsArgs.builder().bucket(name).build())) {
            return
        }
        eduMinioClient.makeBucket(MakeBucketArgs.builder().bucket(name).build())
    }

    private fun getStoragePath(cacheObject: CacheObject): String {
        var name = cacheObject.nodeId.plus("/").plus(cacheObject.hash)
        if (cacheObject.quality != null) {
            name = name.plus("_").plus(cacheObject.quality)
        }
        name = name.plus(".").plus(cacheObject.mimeType.substringAfter("/"))
        return name
    }

    private fun getTempPath(cacheObject: CacheObject): String {
        return cacheObject.type + "/" + cacheObject.nodeId + "/" + cacheObject.hash + "." + cacheObject
            .mimeType.substringAfter("/")
    }
}