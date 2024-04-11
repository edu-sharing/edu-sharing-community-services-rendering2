package org.edu_sharing.rendering.controller.external

import org.edu_sharing.rendering.service.AssetService
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/public/asset")

class AssetController (
    private val assetService: AssetService
) {
    @GetMapping(produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun getAsset(
        @RequestHeader(value = HttpHeaders.RANGE, required = false) range: String = "",
        @RequestParam assetParams: String
    ): ResponseEntity<Resource> {
        val doEncodeData = true
        val asset = assetService.getAsset(assetParams, range)
        val response = ResponseEntity
            .status(if (asset.range != "") HttpStatus.PARTIAL_CONTENT else HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, if (!doEncodeData) asset.mimeType else MediaType.APPLICATION_OCTET_STREAM_VALUE)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CONTENT_LENGTH, asset.fileSize.toString())
        if (asset.range != "") {
            response.header(HttpHeaders.CONTENT_RANGE, asset.range)
        }
        val data = asset.stream.readAllBytes()
        return response.body(ByteArrayResource(data))
    }
}
