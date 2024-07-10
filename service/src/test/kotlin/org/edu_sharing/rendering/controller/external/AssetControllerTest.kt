package org.edu_sharing.rendering.controller.external

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import org.apache.catalina.util.URLEncoder
import org.apache.commons.codec.binary.Base64
import org.edu_sharing.rendering.dto.AssetLinkParams
import org.edu_sharing.rendering.dto.ReadableAsset
import org.edu_sharing.rendering.service.AssetService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AssetController::class, excludeAutoConfiguration = [SecurityAutoConfiguration::class])
class AssetControllerTest (@Autowired val mockMvc: MockMvc) {

    private val assetLinkParams = AssetLinkParams(
        nodeId = "nodeid",
        hash = "hash",
        quality = 0,
        type = "doc",
        mimeType = "application/zip"
    )

    @MockkBean
    lateinit var assetService: AssetService

    @Test
    fun testGetAssetReturnsFullResourceWhenProvidedNoRangeHeader() {
        // Arrange
        val mapper = ObjectMapper()
        val base = Base64().encode(mapper.writeValueAsString(assetLinkParams).toByteArray())
        val encoded = URLEncoder().encode(base.decodeToString(), Charsets.UTF_8)

        val readableAsset = ReadableAsset(
            mimeType = "application/pdf",
            fileSize = 3,
            stream = "qwe".byteInputStream(),
        )

        every { assetService.getAsset(assetLinkParams, "") } returns readableAsset

        // Act and assert
        val result = mockMvc.perform(get("/public/asset?assetParams=$encoded"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Accept-Ranges", "bytes"))
            .andExpect(header().string("Content-Length", "3"))
            .andReturn()

        assert(result.response.contentAsString == "qwe")

        verify (exactly = 1) { assetService.getAsset(assetLinkParams, "") }
        confirmVerified(assetService)
    }

    @Test
    fun testGetAssetReturnsPartialResourceWhenProvidedRangeHeader() {
        // Arrange
        val mapper = ObjectMapper()
        val base = Base64().encode(mapper.writeValueAsString(assetLinkParams).toByteArray())
        val encoded = URLEncoder().encode(base.decodeToString(), Charsets.UTF_8)

        val readableAsset = ReadableAsset(
            mimeType = "video/mpeg",
            fileSize = 3,
            stream = "videopart".byteInputStream(),
            range = "bytes 0-2"
        )

        every { assetService.getAsset(assetLinkParams, "bytes=0-499") } returns readableAsset

        val result = mockMvc.perform(
            get("/public/asset?assetParams=$encoded")
                .header("Range", "bytes=0-499")
        )
            .andExpect(status().isPartialContent)
            .andExpect(header().string("Content-Type", "video/mpeg"))
            .andExpect(header().string("Accept-Ranges", "bytes"))
            .andExpect(header().string("Content-Length", "3"))
            .andExpect(header().string("Content-Range", "bytes 0-2"))
            .andReturn()

        assert(result.response.contentAsString == "videopart")

        verify (exactly = 1) { assetService.getAsset(assetLinkParams, "bytes=0-499") }
        confirmVerified(assetService)
    }

    @Test
    fun testGetStaticAssetReturnsFullResourceWhenProvidedNoRangeHeader() {
        // Arrange
        val readableAsset = ReadableAsset(
            mimeType = "application/pdf",
            fileSize = 3,
            stream = "qwe".byteInputStream(),
        )

        val nodeId = "nodeId"

        val requestSlot = slot<HttpServletRequest>()
        every { assetService.getStaticAsset(capture(requestSlot), "", nodeId) } returns readableAsset

        // Act and assert
        val result = mockMvc.perform(get("/public/asset/static/$nodeId/css/index.css"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Accept-Ranges", "bytes"))
            .andExpect(header().string("Content-Length", "3"))
            .andReturn()

        assert(result.response.contentAsString == "qwe")
        assert(requestSlot.captured.requestURI == "/public/asset/static/nodeId/css/index.css")

        verify (exactly = 1) { assetService.getStaticAsset(any(), "", nodeId) }
        confirmVerified(assetService)
    }
}