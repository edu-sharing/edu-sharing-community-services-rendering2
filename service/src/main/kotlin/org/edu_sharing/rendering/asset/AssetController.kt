package org.edu_sharing.rendering.asset

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.apache.commons.codec.binary.Base64
import org.edu_sharing.rendering.asset.dto.AssetLinkParams
import org.edu_sharing.rendering.asset.dto.ReadableAsset
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.storage.StaticStorageService
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URLDecoder

@RestController
@ConditionalOnController
@RequestMapping(AssetController.ROOT_REQUEST_PATH)
class AssetController(
    private val assetService: AssetService,
    private val storageService: StaticStorageService,
    @Value("\${app.asset.static.frameAncestors}")
    private val allowedFrameAncestors: String?
) {

    companion object{
        const val ROOT_REQUEST_PATH = "/public/asset"
        const val STATIC_ASSET_PATH = "/static"
    }

    @GetMapping(produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun getAsset(
        @RequestHeader(value = HttpHeaders.RANGE, required = false) range: String = "",
        @RequestParam assetParams: String
    ): ResponseEntity<Resource> {
        val doEncodeData = false
        val decoded = Base64().decode(URLDecoder.decode(assetParams, Charsets.UTF_8)).decodeToString()
        val assetLinkParams = ObjectMapper().readValue(decoded, AssetLinkParams::class.java)
        val asset = assetService.getAsset(assetLinkParams, range)
        val headers: MutableMap<String, String> = mutableMapOf()
        if (! allowedFrameAncestors.isNullOrBlank()) {
            headers.put("Content-Security-Policy", "frame-ancestors $allowedFrameAncestors" )
        }
        return prepareResponse(asset = asset, additionalHeaders = headers ,doEncodeData = doEncodeData)
    }

    @GetMapping("$STATIC_ASSET_PATH/**")
    fun getStaticAsset(
        @RequestHeader(value = HttpHeaders.RANGE, required = false) range: String = "",
        request: HttpServletRequest
    ): ResponseEntity<Resource> {
        val (cacheObject, path) = storageService.getCacheObjectFromStaticPath(request.requestURI.substringAfter("$ROOT_REQUEST_PATH$STATIC_ASSET_PATH"))
        val asset = assetService.getStaticAsset(range, cacheObject, path)
        val headers: MutableMap<String, String> = mutableMapOf()
        if (! allowedFrameAncestors.isNullOrBlank()) {
            headers.put("Content-Security-Policy", "frame-ancestors $allowedFrameAncestors" )
        }
        return prepareResponse(asset = asset, additionalHeaders = headers)
    }

    private fun prepareResponse(
        asset: ReadableAsset,
        additionalHeaders: Map<String, String>,
        doEncodeData: Boolean = false): ResponseEntity<Resource> {
        val response = ResponseEntity
            .status(if (asset.range != "") HttpStatus.PARTIAL_CONTENT else HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, if (!doEncodeData) asset.mimeType else MediaType.APPLICATION_OCTET_STREAM_VALUE)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CONTENT_LENGTH, asset.fileSize.toString())
        if (asset.range != "") {
            response.header(HttpHeaders.CONTENT_RANGE, asset.range)
        }
        additionalHeaders.forEach {
            response.header(it.key, it.value)
        }
        val data = asset.stream.readAllBytes()
        return response.body(ByteArrayResource(data))
    }
}
