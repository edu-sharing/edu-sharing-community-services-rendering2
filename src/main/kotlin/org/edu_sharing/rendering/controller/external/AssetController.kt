package org.edu_sharing.rendering.controller.external

import org.edu_sharing.rendering.service.AssetService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/public/asset")

class AssetController (
    private val assetService: AssetService
) {
    @GetMapping()
    fun getAsset(
        @RequestHeader(value = HttpHeaders.RANGE, required = false) range: String,
        @RequestParam file: String
    ): ResponseEntity<ByteArray> {
        val asset = assetService.getAsset(file, range)
        val response = ResponseEntity
            .status(if (asset.range != "") HttpStatus.PARTIAL_CONTENT else HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, asset.mimeType)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CONTENT_LENGTH, asset.fileSize.toString())
        if (asset.range != "") {
            response.header(HttpHeaders.CONTENT_RANGE, asset.range)
        }
        return response.body(asset.stream.readAllBytes())
    }
}
