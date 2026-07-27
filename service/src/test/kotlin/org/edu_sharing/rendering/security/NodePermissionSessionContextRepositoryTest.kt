package org.edu_sharing.rendering.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.generated.repository.backend.services.rest.client.model.NodeRef
import org.edu_sharing.rendering.core.exception.ObjectTypeNotSupportedException
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodePermissionSessionContextRepositoryTest {

    private val moduleRegistry = mockk<ModuleRegistry>()
    private val nodeSessionContextRepository = mockk<NodeSessionContextRepository>(relaxed = true)
    private val underTest =
        NodePermissionSessionContextRepository(10, moduleRegistry, nodeSessionContextRepository)

    @AfterEach
    fun tearDown() = RequestContextHolder.resetRequestAttributes()

    @Test
    fun validateSessionPermissionsFallsBackToDefaultExpirationWhenNoModuleResolves() {
        val session = MockHttpSession()
        session.setAttribute(
            "permissions",
            mutableListOf(NodePermission("repo", "node-1", setOf("Read"), LocalDateTime.now().minusHours(1)))
        )
        val request = MockHttpServletRequest()
        request.setSession(session)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))

        every { nodeSessionContextRepository.getNode("node-1") } returns Node()
        every { moduleRegistry.getRenderModule<RenderModule>(any<Node>()) } throws ObjectTypeNotSupportedException()

        // must not propagate the exception; the permission is expired against the default time and cleaned up
        assertDoesNotThrow { underTest.validateSessionPermissions() }

        assertNull(session.getAttribute("permissions"))
        verify { nodeSessionContextRepository.removeAll(listOf("node-1")) }
    }

    private fun sessionWithPermissions(vararg permissions: NodePermission) {
        val session = MockHttpSession()
        session.setAttribute("permissions", permissions.toMutableList())
        val request = MockHttpServletRequest()
        request.setSession(session)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    @Test
    fun hasPermissionResolvesOriginalIdThroughCollectionReferenceNode() {
        sessionWithPermissions(NodePermission("repo", "ref-1", setOf("ReadAll"), LocalDateTime.now()))
        // the session node repo resolves an original id to the signed reference node
        every { nodeSessionContextRepository.getNode("orig-1") } returns
                Node().ref(NodeRef().id("ref-1"))

        assertTrue(underTest.hasPermission("orig-1", "ReadAll"))
        assertFalse(underTest.hasPermission("orig-1", "DownloadContent"))
        assertEquals("ref-1", underTest.getNodePermission("orig-1")?.nodeId)
    }

    @Test
    fun hasPermissionDeniesWhenNoSessionNodeReferencesTheId() {
        sessionWithPermissions(NodePermission("repo", "ref-1", setOf("ReadAll"), LocalDateTime.now()))
        every { nodeSessionContextRepository.getNode("orig-1") } returns null

        assertFalse(underTest.hasPermission("orig-1", "ReadAll"))
        assertNull(underTest.getNodePermission("orig-1"))
    }

    @Test
    fun hasPermissionDeniesWhenTheResolvedNodeIsNoReference() {
        // a direct node match without its own permission entry must not grant anything
        sessionWithPermissions(NodePermission("repo", "ref-1", setOf("ReadAll"), LocalDateTime.now()))
        every { nodeSessionContextRepository.getNode("node-2") } returns
                Node().ref(NodeRef().id("node-2"))

        assertFalse(underTest.hasPermission("node-2", "ReadAll"))
    }
}