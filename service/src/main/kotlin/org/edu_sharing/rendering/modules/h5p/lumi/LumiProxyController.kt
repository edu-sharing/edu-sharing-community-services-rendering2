package org.edu_sharing.rendering.modules.h5p.lumi

import jakarta.servlet.http.HttpServletRequest
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.edu_sharing.rendering.utils.LogSanitizer
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(H5P_BASE_PATH)
@ConditionalOnController
class LumiProxyController(
    val lumiProxyService: LumiProxyService,
    val lumiContentManagementService: LumiContentManagementService,
    val module: H5pRenderModule
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/{contentId}")
    fun getContent(
        @PathVariable contentId: String,
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
    ): ResponseEntity<Resource> {
        log.debug("Proxy getContent: contentId={}, path={}", contentId, request.requestURI)
        val nodeInfo = lumiContentManagementService.getNodeInfo(contentId)
        val cspHeader = lumiContentManagementService.getCspHeader(nodeInfo.nodeId)
        val additionalHeader = cspHeader?.let {
            mapOf("Content-Security-Policy" to it)
        }
        val result =  lumiProxyService.processProxyRequest(
            pathPrefix = H5P_BASE_PATH,
            nodeInfo = nodeInfo,
            body = body,
            method = method,
            request = request,
            additionalHeaders = additionalHeader ?: emptyMap()
        )
        return result
    }

    @GetMapping("/content/{contentId}/**")
    fun getContentAssets(
        @PathVariable contentId: String,
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
    ): ResponseEntity<Resource> {
        log.debug("Proxy getContentAssets: contentId={}, path={}", contentId, request.requestURI)
        return lumiProxyService.processProxyRequest(
            H5P_BASE_PATH,
            lumiContentManagementService.getNodeInfo(contentId),
            body,
            method,
            request
        )
    }

    /**
     * Static assets the player page loads, forwarded without a node permission check: H5P core, library files
     * (global and per-package cache) and MathJax. Deliberately an allowlist and not a catch-all: lumi runs
     * h5p-express with a permit-all permission system, so a catch-all would hand every session
     * `/download/{contentId}` (the whole package of any content), `/params/{contentId}` and lumi's internal
     * `/edusharing/` API. Anything else below [H5P_BASE_PATH] ends in [rejectUnlistedPath]. The prefixes are
     * lumi's `coreUrl`/`librariesUrl` defaults, `PACKAGE_LIBRARIES_SEGMENT` and `MATHJAX_PATH`.
     */
    @GetMapping("/core/**", "/libraries/**", "/package-libraries/**", "/mathjax/**")
    fun getH5PCoreAssets(
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
    ): ResponseEntity<Resource> {
        log.debug("Proxy getH5PCoreAssets: path={}", request.requestURI)
        return lumiProxyService.processProxyRequest(
            H5P_BASE_PATH,
            body,
            method,
            request
        )
    }

    /**
     * Every path below [H5P_BASE_PATH] that none of the routes above allows, for any method: never forwarded to
     * lumi. Mapped explicitly so it is a 404 - without it Spring's resource fallback throws
     * `NoResourceFoundException`, which the global handler turns into a 500.
     */
    @RequestMapping("/**")
    fun rejectUnlistedPath(request: HttpServletRequest): ResponseEntity<Resource> =
        throw ResourceNotFoundException("No lumi route for ${LogSanitizer.sanitize(request.requestURI)}")
}
