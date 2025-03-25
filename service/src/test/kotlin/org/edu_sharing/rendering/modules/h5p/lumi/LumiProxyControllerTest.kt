package org.edu_sharing.rendering.modules.h5p.lumi

import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiNodeInfo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap


@WebMvcTest(
    LumiProxyController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class],
    properties = ["app.asset.static.frameAncestors=test"]
)
class LumiProxyControllerTest(@Autowired val mockMvc: MockMvc) {
    @MockkBean
    lateinit var lumiProxyService: LumiProxyService

    @MockkBean
    lateinit var lumiContentManagementService: LumiContentManagementService

    @Test
    fun testGetContentCallsServiceWithCorrectParamsAndReturnsResponse() {
        // Arrange
        val nodeInfo = mockk<LumiNodeInfo>()
        val contentId = "abc123"

        val bodySlot = mutableListOf<String?>()
        val methodSlot = slot<HttpMethod>()
        val requestSlot = slot<HttpServletRequest>()
        val headerMap = HashMap<String, List<String>>()
        headerMap["Content-Type"] = listOf("text/html")
        headerMap["Content-Length"] = listOf("9")

        val multiValueMap: MultiValueMap<String, String> = LinkedMultiValueMap()
        headerMap.forEach { (key, value) -> multiValueMap[key] = value }

        val responseEntity = ResponseEntity("mycontent", multiValueMap, HttpStatus.OK)

        every { lumiContentManagementService.getNodeInfo(contentId) } returns nodeInfo
        every {
            lumiProxyService.processProxyRequest(
                H5P_BASE_PATH,
                nodeInfo,
                captureNullable(bodySlot),
                capture(methodSlot),
                capture(requestSlot),
                any(),
                String::class.java
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
        assert(result.response.headerNames.size == 3)
        assert(result.response.getHeaderValue("Content-Length") == 9L)
        assert(result.response.getHeaderValue("Content-Type") == "text/html")
        assert(result.response.getHeaderValue("Content-Security-Policy") == "frame-ancestors test")

        verifySequence {
            lumiContentManagementService.getNodeInfo(contentId)
            lumiProxyService.processProxyRequest(
                H5P_BASE_PATH,
                nodeInfo,
                captureNullable(bodySlot),
                capture(methodSlot),
                capture(requestSlot),
                any(),
                String::class.java
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
        val headerMap = HashMap<String, List<String>>()
        headerMap["Content-Type"] = listOf("text/html")
        headerMap["Content-Length"] = listOf("9")

        val multiValueMap: MultiValueMap<String, String> = LinkedMultiValueMap()
        headerMap.forEach { (key, value) -> multiValueMap[key] = value }

        val responseEntity = ResponseEntity("mycontent".toByteArray(), multiValueMap, HttpStatus.OK)

        every { lumiContentManagementService.getNodeInfo(contentId) } returns nodeInfo
        every {
            lumiProxyService.processProxyRequest(
                H5P_BASE_PATH,
                nodeInfo,
                captureNullable(bodySlot),
                capture(methodSlot),
                capture(requestSlot),
                any(),
                ByteArray::class.java
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
        assert(result.response.headerNames.size == 2)
        assert(result.response.getHeaderValue("Content-Length") == 9L)
        assert(result.response.getHeaderValue("Content-Type") == "text/html")

        verifySequence {
            lumiContentManagementService.getNodeInfo(contentId)
            lumiProxyService.processProxyRequest(
                H5P_BASE_PATH,
                nodeInfo,
                captureNullable(bodySlot),
                capture(methodSlot),
                capture(requestSlot),
                any(),
                ByteArray::class.java
            )
        }
    }

    @Test
    fun testGetH5PCoreAssetsCallsServiceWithCorrectParamsAndReturnsResponse() {
        // Arrange
        val bodySlot = mutableListOf<String?>()
        val methodSlot = slot<HttpMethod>()
        val requestSlot = slot<HttpServletRequest>()
        val headerMap = HashMap<String, List<String>>()
        headerMap["Content-Type"] = listOf("text/javascript")
        headerMap["Content-Length"] = listOf("9")

        val multiValueMap: MultiValueMap<String, String> = LinkedMultiValueMap()
        headerMap.forEach { (key, value) -> multiValueMap[key] = value }

        val responseEntity = ResponseEntity("mycontent".toByteArray(), multiValueMap, HttpStatus.OK)
        every {
            lumiProxyService.processProxyRequest(
                H5P_BASE_PATH,
                captureNullable(bodySlot),
                capture(methodSlot),
                capture(requestSlot),
                any(),
                ByteArray::class.java
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
        assert(result.response.headerNames.size == 2)
        assert(result.response.getHeaderValue("Content-Length") == 9L)
        assert(result.response.getHeaderValue("Content-Type") == "text/javascript")

        verify(exactly = 1) {
            lumiProxyService.processProxyRequest(
                H5P_BASE_PATH,
                captureNullable(bodySlot),
                capture(methodSlot),
                capture(requestSlot),
                any(),
                ByteArray::class.java
            )
        }

        confirmVerified(lumiProxyService, lumiContentManagementService)
    }
}