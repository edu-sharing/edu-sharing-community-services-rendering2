package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.ReadableAsset
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.springframework.stereotype.Service

@Service
class AssetService (
    private val storageImplementation: StorageService,
    private val mapper: Mapper
) {
    private val defaultChunkSize = 2000000
    fun getAsset(requestParam: String, range: String): ReadableAsset {
        val cacheObject = mapper.fileRequestParamToCacheObject(requestParam)
        val objectStats = storageImplementation.getFileProperties(cacheObject)
        if (range !== "") {
            return ReadableAsset(
                mimeType = objectStats.contentType(),
                fileSize = objectStats.size(),
                stream = storageImplementation.getObjectStream(cacheObject)
            )
        }
        val longRange = parseRange(range)
        return ReadableAsset(
            mimeType = objectStats.contentType(),
            fileSize = objectStats.size(),
            range = "bytes " + longRange.first + "-" + longRange.last + "/" + objectStats.size(),
            stream = storageImplementation.getObjectChunkStream(
                cacheObject,
                false,
                longRange.first,
                longRange.last
            )
        )
    }

    private fun parseRange(range: String): LongRange {
        val pattern = "(^[a-zA-Z]\\w*)\\s+(\\d+)\\s?-\\s?(\\d+)?\\s?/?\\s?(\\d+|\\*)?"
        if (! Regex(pattern).matches(range)) {
            throw IllegalArgumentException()
        }
        val numericalRange = range.split(" ")[1]
        val (start, end) = numericalRange.split("-")
        return LongRange(
            start.toLong(),
            if (end != "") end.toLong() else start.toLong() + defaultChunkSize
        )
    }
}