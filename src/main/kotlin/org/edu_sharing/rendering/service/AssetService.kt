package org.edu_sharing.rendering.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.codec.binary.Base64
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.config.annotation.ConditionalOnController
import org.edu_sharing.rendering.dto.AssetLinkParams
import org.edu_sharing.rendering.dto.ReadableAsset
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.springframework.stereotype.Service
import java.net.URLDecoder

@ConditionalOnController
@Service
class AssetService (
    private val storageImplementation: StorageService,
    private val mapper: Mapper
) {
    private val defaultChunkSize = 2000000
    fun getAsset(requestParam: String, range: String): ReadableAsset {
        val decoded = Base64().decode(URLDecoder.decode(requestParam, Charsets.UTF_8)).decodeToString()
        val assetParams = ObjectMapper().readValue(decoded, AssetLinkParams::class.java)
        val cacheObject = mapper.assetLinkParamsToCacheObject(assetParams)
        val objectStats = storageImplementation.getFileProperties(cacheObject)
        if (range == "") {
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
        val numericalRange = range.split(if (range.contains("=")) "=" else " ")[1]
        val (start, end) = numericalRange.split("-", limit = 2)
        return LongRange(
            start.toLong(),
            if (end != "") end.toLong() else start.toLong() + defaultChunkSize
        )
    }
}