package org.edu_sharing.rendering.blobStorage

import com.fasterxml.jackson.databind.ObjectMapper
import io.minio.*
import org.apache.catalina.util.URLEncoder
import org.apache.commons.codec.binary.Base64
import org.edu_sharing.rendering.dto.AssetLinkParams
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.ObjectLink
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.io.InputStream

@Service
class MinioService(
    private val eduMinioClient: MinioClient
) : StorageService {

    @Value("\${app.public.url}")
    lateinit var publicUrl: String
    @Value("\${app.public.port}")
    lateinit var port: String

    private val logger = LoggerFactory.getLogger(javaClass)
    override fun putObject(cacheObject: CacheObject, inputStream: InputStream, metadata: Map<String, String>) {
        createBucket(cacheObject.type)
        eduMinioClient.putObject(
            PutObjectArgs.builder()
                .bucket(cacheObject.type)
                .`object`(getStoragePath(cacheObject))
                .stream(inputStream, cacheObject.size, if(cacheObject.size < 0) 10485760 else -1)
                .userMetadata(metadata)
                .contentType(cacheObject.mimeType)
                .build()
        )
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
        val metadata = getFileProperties(cacheObject).userMetadata()
        if (metadata.containsKey("width")) {
            objectLink.width = metadata["width"]?.toInt() ?: 0
        }
        if (metadata.containsKey("height")) {
            objectLink.height = metadata["height"]?.toInt() ?: 0
        }
        return objectLink
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

    override fun getObjectChunkStream(
        cacheObject: CacheObject,
        isTemp: Boolean,
        offset: Long,
        length: Long
    ): GetObjectResponse {
        return eduMinioClient.getObject(
            GetObjectArgs.Builder()
                .bucket(if (isTemp) "temp" else cacheObject.type)
                .`object`(if (isTemp) getTempPath(cacheObject) else getStoragePath(cacheObject))
                .offset(offset)
                .length(length)
                .build()
        )
    }

    override fun putTempFile(cacheObject: CacheObject, inputStream: InputStream) {
        createBucket("temp")
        eduMinioClient.putObject(
            PutObjectArgs.builder()
                .bucket("temp")
                .`object`(this.getTempPath(cacheObject))
                .stream(inputStream, cacheObject.size, if(cacheObject.size < 0) 10485760 else -1)
                .contentType(cacheObject.mimeType)
                .build()
        )
    }

    override fun isObjectExisting(cacheObject: CacheObject): Boolean {
        try {
            eduMinioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(cacheObject.type)
                    .`object`(this.getStoragePath(cacheObject)).build()
            )
            return true
        } catch (exception: Exception) {
            return false
        }
    }

    override fun getFileProperties(cacheObject: CacheObject): StatObjectResponse {
        return eduMinioClient.statObject(
            StatObjectArgs.builder().bucket(cacheObject.type).`object`(getStoragePath(cacheObject)).build()
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