package org.edu_sharing.rendering.modules.h5p.lumi

import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.junit.jupiter.api.Test
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
        val result = mockMvc.perform(get("$H5P_BASE_PATH/test/myfile"))
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
}
