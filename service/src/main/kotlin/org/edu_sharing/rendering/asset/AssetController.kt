package org.edu_sharing.rendering.asset

import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.apache.commons.codec.binary.Base64
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.asset.dto.AssetLinkParams
import org.edu_sharing.rendering.asset.dto.ReadableAsset
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.security.NodePermissionSessionContextRepository
import org.edu_sharing.rendering.security.NodeSessionContextRepository
import org.edu_sharing.rendering.storage.StaticStorageService
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
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
    private val nodePermissionSessionContextRepository: NodePermissionSessionContextRepository,
    private val nodeSessionRepo: NodeSessionContextRepository,
    private val moduleRegistry: ModuleRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object{
        const val ROOT_REQUEST_PATH = "/public/asset"
        const val STATIC_ASSET_PATH = "/static"
    }

    @GetMapping(produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun getAsset(
        @RequestHeader(value = HttpHeaders.RANGE, required = false) range: String = "",
        @RequestParam assetParams: String
    ): ResponseEntity<Resource> {
        val decoded = Base64().decode(URLDecoder.decode(assetParams, Charsets.UTF_8)).decodeToString()
        val assetLinkParams = ObjectMapper().readValue(decoded, AssetLinkParams::class.java)
        log.debug("Asset request: nodeId=${assetLinkParams.nodeId}, repoId=${assetLinkParams.repoId}, rangePresent=${range.isNotBlank()}")
        val asset = assetService.getAsset(assetLinkParams, range)
        var doEncodeData = false
        if (asset.mimeType == "application/pdf") {
            doEncodeData = !nodePermissionSessionContextRepository.hasPermission(assetLinkParams.nodeId, "DownloadContent")
            log.debug("PDF asset: nodeId=${assetLinkParams.nodeId}, encodeForDownloadRestriction=$doEncodeData")
        }
        val node = nodeSessionRepo.getNode(assetLinkParams.nodeId) ?: throw IllegalStateException("Node not found in session for asset with nodeId: ${assetLinkParams.nodeId}")
        return prepareResponse(asset = asset,doEncodeData = doEncodeData, node = node, repoId = assetLinkParams.repoId)
    }

    @GetMapping("$STATIC_ASSET_PATH/**")
    fun getStaticAsset(
        @RequestHeader(value = HttpHeaders.RANGE, required = false) range: String = "",
        request: HttpServletRequest
    ): ResponseEntity<Resource> {
        val (cacheObject, path) = storageService.getCacheObjectFromStaticPath(request.requestURI.substringAfter("$ROOT_REQUEST_PATH$STATIC_ASSET_PATH"))
        log.debug("Static asset request: nodeId=${cacheObject.nodeId}, repoId=${cacheObject.repoId}, path=$path, rangePresent=${range.isNotBlank()}")
        val asset = assetService.getStaticAsset(range, cacheObject, path)
        val node = nodeSessionRepo.getNode(cacheObject.nodeId) ?: throw IllegalStateException("Node not found in session for asset with nodeId: ${cacheObject.nodeId}")
        return prepareResponse(asset = asset, node = node, repoId = cacheObject.repoId)
    }

    private fun prepareResponse(
        asset: ReadableAsset,
        doEncodeData: Boolean = false,
        node: Node,
        repoId: String
    ): ResponseEntity<Resource> {
        val module = moduleRegistry.getRenderModule<RenderModule>(node)
        val cspHeader = module.getCspHeader(repoId)
        val additionalHeaders = mutableMapOf<String, String>()
        if (cspHeader != null) {
            additionalHeaders["Content-Security-Policy"] = cspHeader
        }
        val isPartial = asset.range.isNotEmpty()
        log.debug("Preparing asset response: mimeType=${asset.mimeType}, isPartial=$isPartial, fileSize=${asset.fileSize}, chunkSize=${asset.chunkSize}, hasCsp=${cspHeader != null}")
        val response = ResponseEntity
            .status(if (asset.range != "") HttpStatus.PARTIAL_CONTENT else HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, if (!doEncodeData) asset.mimeType else MediaType.APPLICATION_OCTET_STREAM_VALUE)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CONTENT_LENGTH, if (isPartial) asset.chunkSize.toString() else asset.fileSize.toString())
        if (isPartial) {
            response.header(HttpHeaders.CONTENT_RANGE, asset.range)
        }
        additionalHeaders.forEach {
            response.header(it.key, it.value)
        }
        return response.body(InputStreamResource(asset.stream))
    }
}
