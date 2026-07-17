package org.edu_sharing.rendering.security

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.generated.repository.backend.services.rest.client.model.NodeRef
import org.edu_sharing.rendering.utils.COLLECTION_REFERENCE_ASPECT
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NodeSessionContextRepositoryTest {

    private val underTest = NodeSessionContextRepository()

    @BeforeEach
    fun setUp() {
        val request = MockHttpServletRequest()
        request.setSession(MockHttpSession())
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    @AfterEach
    fun tearDown() = RequestContextHolder.resetRequestAttributes()

    @Test
    fun `resolves a node by its reference id and by its original id`() {
        val referenceNode = Node()
            .ref(NodeRef().id("ref-1"))
            .aspects(listOf(COLLECTION_REFERENCE_ASPECT))
            .properties(mapOf("ccm:original" to listOf("orig-1")))
        underTest.saveNode(referenceNode)

        assertEquals("ref-1", underTest.getNode("ref-1")?.ref?.id)
        // assets and jobs of a collection reference are keyed by the original id
        assertEquals("ref-1", underTest.getNode("orig-1")?.ref?.id)
        assertNull(underTest.getNode("unrelated"))
    }

    @Test
    fun `does not resolve plain nodes by anything but their id`() {
        underTest.saveNode(Node().ref(NodeRef().id("node-1")))

        assertEquals("node-1", underTest.getNode("node-1")?.ref?.id)
        assertNull(underTest.getNode("orig-1"))
    }
}
