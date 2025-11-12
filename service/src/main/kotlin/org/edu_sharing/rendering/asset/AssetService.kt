package org.edu_sharing.rendering.asset

import org.edu_sharing.rendering.asset.dto.AssetLinkParams
import org.edu_sharing.rendering.asset.dto.ReadableAsset
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.CachedObjectDetails
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.storage.StaticStorageService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import java.io.InputStream

@ConditionalOnController
@Service
class AssetService(
    private val storageImplementation: StaticStorageService,
    private val mapper: Mapper
) {
    private val defaultChunkSize = 2000000L

    @PreAuthorize("hasPermission(#assetParams.nodeId, 'Read')")
    fun getAsset(assetParams: AssetLinkParams, range: String): ReadableAsset {
        val cacheObject = mapper.assetLinkParamsToCacheObject(assetParams)
        val fileDetails = storageImplementation.getFileProperties(cacheObject)

        if (range.isBlank()) {
            return ReadableAsset(
                mimeType = fileDetails.mimeType,
                fileSize = fileDetails.size,
                stream = storageImplementation.getObjectStream(cacheObject)
            )
        }

        val longRange = parseRange(range, fileDetails.size)
        val objectChunkStream = storageImplementation.getObjectChunkStream(
            cacheObject = cacheObject,
            length = longRange.last - longRange.first + 1,
            offset = longRange.first,
            isTemp = false
        )
        return createReadableAsset(fileDetails, objectChunkStream, longRange)
    }


    @PreAuthorize("hasPermission(#cacheObject.nodeId, 'Read')")
    fun getStaticAsset(
        range: String,
        cacheObject: CacheObject,
        path: String
    ): ReadableAsset {
        val fileDetails = storageImplementation.getFileProperties(cacheObject, path)
        if (range.isBlank()) {
            return ReadableAsset(
                mimeType = fileDetails.mimeType,
                fileSize = fileDetails.size,
                stream = storageImplementation.getObjectStream(cacheObject, path)
            )
        }

        val longRange = parseRange(range, fileDetails.size)
        val objectChunkStream = storageImplementation.getObjectChunkStream(
            cacheObject = cacheObject,
            path = path,
            offset = longRange.first,
            length = longRange.last - longRange.first + 1,
        )

        return createReadableAsset(fileDetails, objectChunkStream, longRange)
    }

    private fun createReadableAsset(
        fileDetails: CachedObjectDetails,
        inputStream: InputStream,
        longRange: LongRange
    ) = ReadableAsset(
        mimeType = fileDetails.mimeType,
        fileSize = fileDetails.size,
        range = "bytes ${longRange.first}-${longRange.last}/${fileDetails.size}",
        stream = inputStream,
        chunkSize = longRange.last - longRange.first + 1,
    )

    @Throws(NumberFormatException::class, IndexOutOfBoundsException::class)
    private fun parseRange(range: String, fileSize: Long): LongRange {
        if (!(range.startsWith("bytes=") || range.startsWith("bytes "))) {
            throw IllegalArgumentException("Range header must start with 'bytes=' or 'bytes '")
        }
        val numericalRange = range.split(if (range.contains("=")) "=" else " ")[1]
        val (start, end) = numericalRange.split("-", limit = 2)
        if (start.isEmpty()) {
            throw IllegalArgumentException("Range start cannot be empty")
        }
        val startLong = start.toLong()
        val endLong = if (end.isNotEmpty()) {
            val parsedEnd = end.toLong()
            if (parsedEnd < startLong) {
                val chunkSize = minOf(defaultChunkSize, fileSize - startLong)
                startLong + chunkSize - 1
            } else {
                minOf(parsedEnd, fileSize - 1)
            }

        } else {
            val chunkSize = minOf(defaultChunkSize, fileSize - startLong)
            startLong + chunkSize - 1
        }

        val actualEnd = minOf(endLong, fileSize - 1)

        return LongRange(startLong, actualEnd)
    }
}
