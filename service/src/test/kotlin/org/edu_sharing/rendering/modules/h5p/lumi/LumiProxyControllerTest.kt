package org.edu_sharing.rendering.modules.h5p.lumi

import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


@WebMvcTest(
    LumiProxyController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class]
)
class LumiProxyControllerTest(@Autowired val mockMvc: MockMvc) {
    @MockkBean
    lateinit var lumiProxyService: LumiProxyService

    @MockkBean
    lateinit var lumiContentManagementService: LumiContentManagementService

    @MockkBean
    lateinit var h5pRenderModule: H5pRenderModule

    @Test
    fun testGetContentCallsServiceWithCorrectParamsAndReturnsResponse() {
        // Arrange
        val nodeInfo = mockk<LumiNodeInfo>()
        val contentId = "abc123"

        val bodySlot = mutableListOf<String?>()
        val methodSlot = slot<HttpMethod>()
        val requestSlot = slot<HttpServletRequest>()
        val headers = HttpHeaders()
        headers.add("Content-Type", "text/html")
        headers.add("Content-Length", "9")

        val responseEntity: ResponseEntity<Resource> =
            ResponseEntity.status(HttpStatus.OK).headers(headers)
                .body(ByteArrayResource("mycontent".toByteArray()) as Resource)

        every { nodeInfo.nodeId } returns "myNodeId"
        every { lumiContentManagementService.getNodeInfo(contentId) } returns nodeInfo
        every { lumiContentManagementService.getCspHeader("myNodeId") } returns "myCspHeader"
        every {
            lumiProxyService.processProxyRequest(
                pathPrefix = H5P_BASE_PATH,
                nodeInfo = nodeInfo,
                body = captureNullable(bodySlot),
                method =capture(methodSlot),
                request= capture(requestSlot),
                additionalHeaders = mapOf("Content-Security-Policy" to "myCspHeader")
            )
        } returns responseEntity

        // Act
        val result = mockMvc.perform(get("$H5P_BASE_PATH/$contentId"))
            .andExpect(status().isOk)
            .andReturn()

        // Assert
        assert(bodySlot.size == 1)
        assert(bodySlot[0] == null)

        assert(methodSlot.captured.name() == HttpMethod.GET.toString())
        assert(requestSlot.captured == result.request)

        assert(result.response.contentAsString == "mycontent")
        assert(result.response.getHeader("Content-Length") == "9")
        assert(result.response.getHeaderValue("Content-Type") == "text/html")

        verifySequence {
            lumiContentManagementService.getNodeInfo(contentId)
            lumiContentManagementService.getCspHeader("myNodeId")
            lumiProxyService.processProxyRequest(
                pathPrefix = H5P_BASE_PATH,
                nodeInfo = nodeInfo,
                body = captureNullable(bodySlot),
                method =capture(methodSlot),
                request= capture(requestSlot),
                additionalHeaders = mapOf("Content-Security-Policy" to "myCspHeader")
            )
        }
    }

    @Test
    fun testGetContentAssetsCallsServiceWithCorrectParamsAndReturnsResponse() {
        // Arrange
        val nodeInfo = mockk<LumiNodeInfo>()
        val contentId = "abc123"

        val bodySlot = mutableListOf<String?>()
        val methodSlot = slot<HttpMethod>()
        val requestSlot = slot<HttpServletRequest>()
        val headers = HttpHeaders()
        headers.add("Content-Type", "text/html")
        headers.add("Content-Length", "9")

        val responseEntity: ResponseEntity<Resource> =
            ResponseEntity.status(HttpStatus.OK).headers(headers)
                .body(ByteArrayResource("mycontent".toByteArray()) as Resource)

        every { lumiContentManagementService.getNodeInfo(contentId) } returns nodeInfo
        every {
            lumiProxyService.processProxyRequest(
                H5P_BASE_PATH,
                nodeInfo,
                captureNullable(bodySlot),
                capture(methodSlot),
                capture(requestSlot)
            )
        } returns responseEntity

        // Act
        val result = mockMvc.perform(get("$H5P_BASE_PATH/content/$contentId/myfile.jpg"))
            .andExpect(status().isOk)
            .andReturn()

        // Assert
        assert(bodySlot.size == 1)
        assert(bodySlot[0] == null)

        assert(methodSlot.captured.name() == HttpMethod.GET.toString())
        assert(requestSlot.captured == result.request)

        assert(result.response.contentAsString == "mycontent")
        assert(result.response.getHeader("Content-Length") == "9")
        assert(result.response.getHeaderValue("Content-Type") == "text/html")

        verifySequence {
            lumiContentManagementService.getNodeInfo(contentId)
            lumiProxyService.processProxyRequest(
                H5P_BASE_PATH,
                nodeInfo,
                captureNullable(bodySlot),
                capture(methodSlot),
                capture(requestSlot)
            )
        }
    }

    @Test
    fun testGetH5PCoreAssetsCallsServiceWithCorrectParamsAndReturnsResponse() {
        // Arrange
        val bodySlot = mutableListOf<String?>()
        val methodSlot = slot<HttpMethod>()
        val requestSlot = slot<HttpServletRequest>()
        val headers = HttpHeaders()
        headers.add("Content-Type", "text/javascript")
        headers.add("Content-Length", "9")

        val responseEntity: ResponseEntity<Resource> =
            ResponseEntity.status(HttpStatus.OK).headers(headers)
                .body(ByteArrayResource("mycontent".toByteArray()) as Resource)
        every {
            lumiProxyService.processProxyRequest(
                H5P_BASE_PATH,
                captureNullable(bodySlot),
                capture(methodSlot),
                capture(requestSlot)
            )
        } returns responseEntity

        // Act
        val result = mockMvc.perform(get("$H5P_BASE_PATH/core/js/h5p.js"))
            .andExpect(status().isOk)
            .andReturn()

        // Assert
        assert(bodySlot.size == 1)
        assert(bodySlot[0] == null)

        assert(methodSlot.captured.name() == HttpMethod.GET.toString())
        assert(requestSlot.captured == result.request)

        assert(result.response.contentAsString == "mycontent")
        assert(result.response.getHeader("Content-Length") == "9")
        assert(result.response.getHeaderValue("Content-Type") == "text/javascript")

        verify(exactly = 1) {
            lumiProxyService.processProxyRequest(
                H5P_BASE_PATH,
                captureNullable(bodySlot),
                capture(methodSlot),
                capture(requestSlot)
            )
        }

        confirmVerified(lumiProxyService, lumiContentManagementService)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/core/js/h5p.js",
            "/libraries/H5P.MultiChoice-1.16/multichoice.js",
            "/package-libraries/abc123/H5P.MultiChoice-1.16/multichoice.js",
            "/mathjax/4.0.0-4.0.0/tex-chtml.js",
        ]
    )
    fun testAllowedStaticAssetPathsAreForwarded(path: String) {
        every { lumiProxyService.processProxyRequest(H5P_BASE_PATH, null, HttpMethod.GET, any()) } returns
            ResponseEntity.ok(ByteArrayResource("asset".toByteArray()) as Resource)

        mockMvc.perform(get("$H5P_BASE_PATH$path"))
            .andExpect(status().isOk)

        verify(exactly = 1) { lumiProxyService.processProxyRequest(H5P_BASE_PATH, null, HttpMethod.GET, any()) }
        confirmVerified(lumiProxyService, lumiContentManagementService)
    }

    /**
     * lumi answers these without any permission check (h5p-express with a permit-all permission system), so they
     * must never reach it: the package export, the content parameters, temporary files, user data and lumi's
     * internal `/edusharing/` API.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "/download/abc123",
            "/params/abc123",
            "/temp-files/some-file.png",
            "/contentUserData/abc123/state/0",
            "/edusharing/buckets",
            "/edusharing/health",
            "/edusharing/nodeid/someNode_someHash",
            "/edusharing/contentid/abc123",
            "/editor/scripts/h5peditor.js",
            "/unknown/path",
        ]
    )
    fun testUnlistedPathsAreNotForwardedToLumi(path: String) {
        mockMvc.perform(get("$H5P_BASE_PATH$path"))
            .andExpect(status().isNotFound)

        confirmVerified(lumiProxyService, lumiContentManagementService)
    }

    /**
     * A single segment such as lumi's `/ajax` endpoint is taken for a content id: it only reaches lumi through
     * the node lookup, which lumi answers with "not found".
     */
    @Test
    fun testSingleSegmentPathIsTreatedAsContentId() {
        every { lumiContentManagementService.getNodeInfo("ajax") } throws
            ResourceNotFoundException("Lumi holds no content with id ajax")

        mockMvc.perform(get("$H5P_BASE_PATH/ajax?action=content-type-cache"))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { lumiContentManagementService.getNodeInfo("ajax") }
        confirmVerified(lumiProxyService, lumiContentManagementService)
    }

    @Test
    fun testNonGetRequestsOnAllowedPathsAreNotForwardedToLumi() {
        mockMvc.perform(post("$H5P_BASE_PATH/core/js/h5p.js"))
            .andExpect(status().isNotFound)

        confirmVerified(lumiProxyService, lumiContentManagementService)
    }
}
