package org.edu_sharing.rendering.asset

import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.asset.dto.ReadableAsset
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.security.NodePermissionSessionContextRepository
import org.edu_sharing.rendering.security.NodeSessionContextRepository
import org.edu_sharing.rendering.storage.StaticStorageService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.mock.web.MockHttpServletRequest

/**
 * Focused coverage for the static-asset wildcard handling. The request URI arrives percent-encoded
 * while storage keys hold the raw archive entry names, so the controller has to decode before
 * looking anything up.
 */
@ExtendWith(MockKExtension::class)
class AssetControllerStaticPathTest {
    private val assetService: AssetService = mockk()
    private val storageService: StaticStorageService = mockk()
    private val nodePermissionSessionContextRepository: NodePermissionSessionContextRepository = mockk()
    private val nodeSessionRepo: NodeSessionContextRepository = mockk()
    private val moduleRegistry: ModuleRegistry = mockk()
    private val renderModule: RenderModule = mockk()

    private val underTest = AssetController(
        assetService = assetService,
        storageService = storageService,
        nodePermissionSessionContextRepository = nodePermissionSessionContextRepository,
        nodeSessionRepo = nodeSessionRepo,
        moduleRegistry = moduleRegistry
    )

    private val cacheObject = CacheObject(
        nodeId = "nodeId123",
        hash = "hash123",
        type = "eduhtml",
        repoId = "repoId123"
    )

    private fun requestFor(requestUri: String): MockHttpServletRequest =
        MockHttpServletRequest("GET", requestUri).apply { this.requestURI = requestUri }

    private fun stubLookup(): CapturingSlot<String> {
        val lookedUpPath = slot<String>()
        every { storageService.getCacheObjectFromStaticPath(capture(lookedUpPath)) } returns
            Pair(cacheObject, "/content/images/Bild Übersicht.png")
        every { assetService.getStaticAsset(any(), any(), any()) } returns ReadableAsset(
            mimeType = "image/png",
            fileSize = 3,
            stream = "png".byteInputStream()
        )
        val node = mockk<Node>(relaxed = true)
        every { nodeSessionRepo.getNode("nodeId123") } returns node
        every { moduleRegistry.getRenderModule<RenderModule>(node, any()) } returns renderModule
        every { renderModule.getCspHeader("repoId123") } returns null
        return lookedUpPath
    }

    @Test
    fun decodesPercentEncodedStaticPathBeforeLookup() {
        val lookedUpPath = stubLookup()

        val response = underTest.getStaticAsset(
            range = "",
            request = requestFor(
                "/public/asset/static/repoId123/eduhtml/nodeId123/hash123/content/images/Bild%20%C3%9Cbersicht.png"
            )
        )

        assert(response.statusCode.value() == 200) { response.statusCode.toString() }
        assert(lookedUpPath.captured == "/repoId123/eduhtml/nodeId123/hash123/content/images/Bild Übersicht.png") {
            lookedUpPath.captured
        }
        verify(exactly = 1) { assetService.getStaticAsset("", cacheObject, "/content/images/Bild Übersicht.png") }
    }

    @Test
    fun keepsLiteralPlusInFileNames() {
        // URLDecoder would turn "+" into a space here; UriUtils treats it as a path character.
        val lookedUpPath = stubLookup()

        underTest.getStaticAsset(
            range = "",
            request = requestFor("/public/asset/static/repoId123/eduhtml/nodeId123/hash123/libs/a+b.js")
        )

        assert(lookedUpPath.captured == "/repoId123/eduhtml/nodeId123/hash123/libs/a+b.js") { lookedUpPath.captured }
    }
}
