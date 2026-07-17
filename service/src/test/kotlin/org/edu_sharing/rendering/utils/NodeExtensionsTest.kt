package org.edu_sharing.rendering.utils

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.generated.repository.backend.services.rest.client.model.NodeRef
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NodeExtensionsTest {

    private fun node(
        refId: String = "ref-1",
        aspects: List<String>? = listOf(COLLECTION_REFERENCE_ASPECT),
        properties: Map<String, List<String>>? = mapOf("ccm:original" to listOf("orig-1"))
    ): Node = Node().ref(NodeRef().id(refId)).aspects(aspects).properties(properties)

    @Test
    fun `resolves the original id of a collection reference`() {
        assertEquals("orig-1", node().collectionRefOriginalId())
        assertEquals("orig-1", node().storageNodeId())
    }

    @Test
    fun `falls back to cm original when ccm original is missing`() {
        val n = node(properties = mapOf("cm:original" to listOf("orig-2")))
        assertEquals("orig-2", n.collectionRefOriginalId())
    }

    @Test
    fun `is no reference without the collection reference aspect`() {
        assertNull(node(aspects = listOf("cm:versionable")).collectionRefOriginalId())
        assertNull(node(aspects = null).collectionRefOriginalId())
        assertEquals("ref-1", node(aspects = null).storageNodeId())
    }

    @Test
    fun `is no reference without an original property`() {
        assertNull(node(properties = null).collectionRefOriginalId())
        assertNull(node(properties = mapOf("cm:name" to listOf("x"))).collectionRefOriginalId())
        assertNull(node(properties = mapOf("ccm:original" to listOf())).collectionRefOriginalId())
        assertNull(node(properties = mapOf("ccm:original" to listOf(""))).collectionRefOriginalId())
    }

    @Test
    fun `is no reference when the original equals the node itself`() {
        val n = node(properties = mapOf("ccm:original" to listOf("ref-1")))
        assertNull(n.collectionRefOriginalId())
        assertEquals("ref-1", n.storageNodeId())
    }
}
