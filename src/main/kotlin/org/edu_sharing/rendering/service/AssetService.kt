package org.edu_sharing.rendering.service

import io.minio.StatObjectResponse
import jakarta.servlet.http.HttpServletRequest
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.config.annotation.ConditionalOnController
import org.edu_sharing.rendering.dto.AssetLinkParams
import org.edu_sharing.rendering.dto.ReadableAsset
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import java.io.InputStream

@ConditionalOnController
@Service
class AssetService(
    private val storageImplementation: StorageService,
    private val mapper: Mapper
) {
    private val defaultChunkSize = 2000000

    @PreAuthorize("hasPermission(#assetParams.nodeId, 'Read')")
    fun getAsset(assetParams: AssetLinkParams, range: String): ReadableAsset {
        val cacheObject = mapper.assetLinkParamsToCacheObject(assetParams)
        val objectStats = storageImplementation.getFileProperties(cacheObject)

        if (range.isBlank()) {
            return ReadableAsset(
                mimeType = objectStats.contentType(),
                fileSize = objectStats.size(),
                stream = storageImplementation.getObjectStream(cacheObject)
            )
        }

        val longRange = parseRange(range)
        val objectChunkStream = storageImplementation.getObjectChunkStream(
            cacheObject,
            false,
            longRange.first,
            longRange.last
        )
        return createReadableAsset(objectStats, objectChunkStream, longRange)
    }


    @PreAuthorize("hasPermission(#nodeId, 'Read')")
    fun getStaticAsset(request: HttpServletRequest, range: String, nodeId: String): ReadableAsset {
        val storagePath = request.requestURI.toString().substringAfter("/static/")
        val objectStats = storageImplementation.getFileProperties("eduhtml", storagePath)

        if (range.isBlank()) {
            return ReadableAsset(
                mimeType = objectStats.contentType(),
                fileSize = objectStats.size(),
                stream = storageImplementation.getObjectStream("eduhtml", storagePath)
            )
        }

        val longRange = parseRange(range)
        val objectChunkStream = storageImplementation.getObjectChunkStream(
            "eduhtml",
            storagePath,
            longRange.first,
            longRange.last
        )

        return createReadableAsset(objectStats, objectChunkStream, longRange)
    }

    private fun createReadableAsset(
        objectStats: StatObjectResponse,
        inputStream: InputStream,
        longRange: LongRange? = null
    ) = ReadableAsset(
        mimeType = objectStats.contentType(),
        fileSize = objectStats.size(),
        range = if (longRange != null) "bytes ${longRange.first}-${longRange.last}/${objectStats.size()}" else "",
        stream = inputStream
    )

    private fun parseRange(range: String): LongRange {
        val numericalRange = range.split(if (range.contains("=")) "=" else " ")[1]
        val (start, end) = numericalRange.split("-", limit = 2)
        return LongRange(
            start.toLong(),
            if (end != "") end.toLong() else start.toLong() + defaultChunkSize
        )
    }
}
