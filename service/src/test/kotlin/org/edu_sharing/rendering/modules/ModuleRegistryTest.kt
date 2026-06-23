package org.edu_sharing.rendering.modules

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Content
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.junit.jupiter.api.Test
import kotlin.test.assertSame

class ModuleRegistryTest {

    private fun stubModule(name: String, fallsThrough: Boolean = false) = object : RenderModule {
        override fun module() = name
        override fun handle(node: Node) = RenderDataResponse(module = name)
        override fun fallsThroughOnLocalContent() = fallsThrough
    }

    private fun mapperOf(vararg associations: Pair<ModuleTypeDefinition, RenderModule>) =
        object : ModuleTypeMapper {
            override fun moduleTypeAssociations() = associations.toList()
        }

    private val omega = stubModule("OMEGA", fallsThrough = true)
    private val sodix = stubModule("SODIX") // does NOT opt into fall-through
    private val image = stubModule("IMAGE")

    private val underTest = ModuleRegistry(
        listOf(
            mapperOf(ModuleTypeDefinition(replicationSource = "DE.FWU") to omega),
            mapperOf(ModuleTypeDefinition(replicationSource = "SODIX") to sodix),
            mapperOf(ModuleTypeDefinition(mimeTypePrefix = "image") to image),
        )
    )

    private fun node(
        replicationSource: String? = null,
        mimetype: String = "image/png",
        location: String? = null,
        hash: String? = null,
    ): Node {
        val props = mutableMapOf<String, MutableList<String>>()
        replicationSource?.let { props["ccm:replicationsource"] = mutableListOf(it) }
        location?.let { props["cclom:location"] = mutableListOf(it) }
        val n = Node().mimetype(mimetype).properties(props)
        hash?.let { n.content(Content().hash(it)) }
        return n
    }

    @Test
    fun fwuNodeWithLocalContentFallsThroughToMimetypeModule() {
        // local content = no cclom:location AND a content hash present
        val resolved = underTest.getRenderModule<RenderModule>(
            node(replicationSource = "DE.FWU", location = null, hash = "abc123")
        )
        assertSame(image, resolved)
    }

    @Test
    fun fwuNodeWithExternalLocationDoesNotFallThrough() {
        // cclom:location present => not local content => OMEGA handles it
        val resolved = underTest.getRenderModule<RenderModule>(
            node(replicationSource = "DE.FWU", location = "http://external/stream", hash = "abc123")
        )
        assertSame(omega, resolved)
    }

    @Test
    fun fwuNodeWithoutContentHashDoesNotFallThrough() {
        // no content hash => not local content => OMEGA handles it
        val resolved = underTest.getRenderModule<RenderModule>(
            node(replicationSource = "DE.FWU", location = null, hash = null)
        )
        assertSame(omega, resolved)
    }

    @Test
    fun nonOptInReplicationSourceModuleIsNotSkippedOnLocalContent() {
        // SODIX does not opt into fall-through: even with local content it still resolves to SODIX
        val resolved = underTest.getRenderModule<RenderModule>(
            node(replicationSource = "SODIX", location = null, hash = "abc123")
        )
        assertSame(sodix, resolved)
    }
}
