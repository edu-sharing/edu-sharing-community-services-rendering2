package org.edu_sharing.rendering.asset

import com.ninjasquad.springmockk.MockkBean
import org.edu_sharing.rendering.asset.dto.AssetLinkParams
import org.edu_sharing.rendering.storage.StaticStorageService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc


@WebMvcTest(
    AssetController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class],
    properties = ["app.asset.static.frameAncestors=test"]
)
class AssetControllerTest (@Autowired val mockMvc: MockMvc) {

    private val assetLinkParams = AssetLinkParams(
        nodeId = "nodeid",
        hash = "hash",
        quality = 0,
        type = "doc",
        mimeType = "application/zip",
        repoId = ""
    )

    @MockkBean
    lateinit var assetService: AssetService

    @MockkBean
    lateinit var storageService: StaticStorageService

    /*
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

        val repoId = "repoId123"
        val nodeId = "nodeId123"
        val hash = "hash123"
        val type = "type123"

        val cacheObject = mockk<CacheObject>()

        every { storageService.getCacheObjectFromStaticPath("/$repoId/$nodeId/$hash/$type/css/index.css") } returns Pair(cacheObject, "mypath")
        every { assetService.getStaticAsset("", cacheObject, "mypath") } returns readableAsset

        // Act and assert
        val result = mockMvc.perform(get("/public/asset/static/$repoId/$nodeId/$hash/$type/css/index.css"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Accept-Ranges", "bytes"))
            .andExpect(header().string("Content-Length", "3"))
            .andExpect(header().string("Content-Security-Policy", "frame-ancestors test"))
            .andReturn()

        assert(result.response.contentAsString == "qwe")

        verifySequence {
            storageService.getCacheObjectFromStaticPath("/$repoId/$nodeId/$hash/$type/css/index.css")
            assetService.getStaticAsset("", cacheObject, "mypath")
        }
    }
}

@WebMvcTest(
    AssetController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class],
    properties = ["app.asset.static.frameAncestors="]
)
class AssetControllerNoAncestorsTest(@Autowired val mockMvc: MockMvc) {
    @MockkBean
    lateinit var assetService: AssetService

    @MockkBean
    lateinit var storageService: StaticStorageService

    @Test
    fun testGetStaticAssetReturnsFullResourceWhenProvidedNoRangeHeader() {
        // Arrange
        val readableAsset = ReadableAsset(
            mimeType = "application/pdf",
            fileSize = 3,
            stream = "qwe".byteInputStream(),
        )

        val repoId = "repoId123"
        val nodeId = "nodeId123"
        val hash = "hash123"
        val type = "type123"

        val cacheObject = mockk<CacheObject>()

        every { storageService.getCacheObjectFromStaticPath("/$repoId/$nodeId/$hash/$type/css/index.css") } returns Pair(cacheObject, "mypath")
        every { assetService.getStaticAsset("", cacheObject, "mypath") } returns readableAsset

        // Act and assert
        val result = mockMvc.perform(get("/public/asset/static/$repoId/$nodeId/$hash/$type/css/index.css"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Accept-Ranges", "bytes"))
            .andExpect(header().string("Content-Length", "3"))
            .andReturn()

        assert(result.response.contentAsString == "qwe")

        verifySequence {
            storageService.getCacheObjectFromStaticPath("/$repoId/$nodeId/$hash/$type/css/index.css")
            assetService.getStaticAsset("", cacheObject, "mypath")
        }
    }

     */

}
