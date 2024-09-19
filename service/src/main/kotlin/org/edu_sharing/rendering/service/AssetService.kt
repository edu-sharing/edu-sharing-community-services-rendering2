package org.edu_sharing.rendering.service

import jakarta.servlet.http.HttpServletRequest
import org.edu_sharing.rendering.blobStorage.StaticStorageService
import org.edu_sharing.rendering.config.annotation.ConditionalOnController
import org.edu_sharing.rendering.dto.AssetLinkParams
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.CachedObjectDetails
import org.edu_sharing.rendering.dto.ReadableAsset
import org.edu_sharing.rendering.dto.mapper.Mapper
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
            cacheObject,
            longRange.last,
            longRange.first,
            false
        )
        return createReadableAsset(fileDetails, objectChunkStream, longRange)
    }


    @PreAuthorize("hasPermission(#nodeId, 'Read')")
    fun getStaticAsset(request: HttpServletRequest, range: String, repoId: String, nodeId: String, hash: String, type: String): ReadableAsset {
        // build cache object
        // /public/assets/static/<cacheObjectStuff>/index.html
        // /public/assets/static/<cacheObjectStuff>/123/whatever.html
        val cacheObject = CacheObject.of(repoId, nodeId, hash, type)
        val storagePath = request.requestURI.substringAfter("/static/${repoId}/${nodeId}/${hash}/${type}/")

        val fileDetails = storageImplementation.getFileProperties(cacheObject)
        if (range.isBlank()) {
            return ReadableAsset(
                mimeType = fileDetails.mimeType,
                fileSize = fileDetails.size,
                stream = storageImplementation.getObjectStream(cacheObject, storagePath)
            )
        }

        val longRange = parseRange(range, fileDetails.size)
        val objectChunkStream = storageImplementation.getObjectChunkStream(
            cacheObject,
            storagePath,
            longRange.first,
            longRange.last
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
        stream = inputStream
    )

    @Throws(NumberFormatException::class, IndexOutOfBoundsException::class)
    private fun parseRange(range: String, fileSize: Long): LongRange {
        val numericalRange = range.split(if (range.contains("=")) "=" else " ")[1]
        val (start, end) = numericalRange.split("-", limit = 2)
        val startLong = start.toLong()
        val remainingBytes = fileSize-startLong
        val desiredSize = if (end != ""  && startLong >= end.toLong()) {
            defaultChunkSize
        } else if (end == "") {
            defaultChunkSize
        } else {
            end.toLong() - startLong
        }
        val chunkSize = if (desiredSize >= remainingBytes) {
            remainingBytes-1
        } else {
            desiredSize
        }
        return LongRange(
            startLong,
            start.toLong() + chunkSize
        )
    }
}
