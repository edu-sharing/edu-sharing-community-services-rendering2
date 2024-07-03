package org.edu_sharing.rendering.blobStorage

import com.fasterxml.jackson.databind.ObjectMapper
import io.minio.*
import org.apache.catalina.util.URLEncoder
import org.apache.commons.codec.binary.Base64
import org.apache.tika.mime.MimeTypes
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
       putObject(cacheObject, inputStream, getStoragePath(cacheObject), metadata)
    }

    override fun putObject(
        cacheObject: CacheObject,
        inputStream: InputStream,
        targetPath: String,
        metadata: Map<String, String>
    ) {
        createBucket(cacheObject.type)
        val args = PutObjectArgs.builder()
            .bucket(cacheObject.type)
            .`object`(targetPath)
            .stream(inputStream, cacheObject.size, if(cacheObject.size < 0) 10485760 else -1)
            .userMetadata(metadata)
        if (cacheObject.mimeType.isNotBlank()) {
            args.contentType(cacheObject.mimeType)
        }
        eduMinioClient.putObject(args.build())
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
        if (metadata.containsKey("isHighestResolution") && metadata["isHighestResolution"].toBoolean()) {
            objectLink.isHighestQuality = true
        }
        return objectLink
    }

    override fun getObjectLink(path: String): ObjectLink {
        val url = UriComponentsBuilder.newInstance()
            .scheme(publicUrl.substringBefore("://"))
            .host(publicUrl.substringAfter("://"))
            .port(port)
            .path("/public/asset/static/${path.trimStart {it == '/'}}")
            .build()
            .toUriString()
        return ObjectLink(link = url)
    }

    override fun removeObject(cacheObject: CacheObject, isTemp: Boolean) {
        eduMinioClient.removeObject(RemoveObjectArgs.builder().bucket(if (isTemp) "temp" else cacheObject.type)
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

    override fun getObjectStream(bucket: String, path: String): GetObjectResponse {
        return eduMinioClient.getObject(
            GetObjectArgs.Builder()
                .bucket(bucket)
                .`object`(path)
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

    override fun getObjectChunkStream(bucket: String, path: String, offset: Long, length: Long): GetObjectResponse {
        return eduMinioClient.getObject(
            GetObjectArgs.Builder()
                .bucket(bucket)
                .`object`(path)
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

    override fun getFileProperties(cacheObject: CacheObject): StatObjectResponse {
        return eduMinioClient.statObject(
            StatObjectArgs.builder().bucket(cacheObject.type).`object`(getStoragePath(cacheObject)).build()
        )
    }

    override fun getFileProperties(bucket: String, path: String): StatObjectResponse {
        return eduMinioClient.statObject(
            StatObjectArgs.builder().bucket(bucket).`object`(path).build()
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
        name = name.plus(".").plus(getExtensionFromMimeType(cacheObject.mimeType))
        return name
    }

    private fun getTempPath(cacheObject: CacheObject): String {
        return cacheObject.type + "/" + cacheObject.nodeId + "/" + cacheObject.hash + "." +
                getExtensionFromMimeType(cacheObject.mimeType)
    }

    private fun getExtensionFromMimeType(mimeType: String): String {
        return MimeTypes.getDefaultMimeTypes().forName(mimeType).extension
    }
}
