package org.edu_sharing.rendering.modules

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Content
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Remote
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Repo
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.exception.ObjectTypeNotSupportedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertSame

class ModuleRegistryTest {

    private fun stubModule(name: String, fallsThrough: Boolean = false, optional: Boolean = false) = object : RenderModule {
        override fun module() = name
        override fun handle(node: Node) = RenderDataResponse(module = name)
        override fun fallsThroughOnLocalContent() = fallsThrough
        override fun isOptionalModule() = optional
    }

    private fun mapperOf(vararg associations: Pair<ModuleTypeDefinition, RenderModule>) =
        object : ModuleTypeMapper {
            override fun moduleTypeAssociations() = associations.toList()
        }

    private val omega = stubModule("OMEGA", fallsThrough = true)
    private val sodix = stubModule("SODIX") // does NOT opt into fall-through
    private val binder = stubModule("BINDER")
    private val image = stubModule("IMAGE")
    private val jupyterByType = stubModule("JUPYTER_TYPE", optional = true)
    private val jupyterByMime = stubModule("JUPYTER_MIME", optional = true)

    // Filter that mimics repo gating: non-optional modules always allowed, optional modules only if activated.
    private fun onlyActivated(vararg activated: String): (RenderModule) -> Boolean =
        { !it.isOptionalModule() || it.module() in activated }

    private val underTest = ModuleRegistry(
        listOf(
            mapperOf(ModuleTypeDefinition(replicationSource = "DE.FWU") to omega),
            mapperOf(ModuleTypeDefinition(replicationSource = "SODIX") to sodix),
            mapperOf(ModuleTypeDefinition(resourceType = "git-binder") to binder),
            mapperOf(ModuleTypeDefinition(mimeTypePrefix = "image") to image),
            mapperOf(ModuleTypeDefinition(type = "file-jupyter") to jupyterByType),
            mapperOf(ModuleTypeDefinition(mimeTypePrefix = "application", mimeTypeSuffix = "x-ipynb+json") to jupyterByMime),
        )
    )

    private fun node(
        mediatype: String? = null,
        replicationSource: String? = null,
        resourceType: String? = null,
        mimetype: String = "image/png",
        location: String? = null,
        hash: String? = null,
        wwwUrl: String? = null,
        ltiUrl: String? = null,
        connectorRenderUrl: String? = null,
    ): Node {
        val props = mutableMapOf<String, MutableList<String>>()
        replicationSource?.let { props["ccm:replicationsource"] = mutableListOf(it) }
        resourceType?.let { props["ccm:ccressourcetype"] = mutableListOf(it) }
        location?.let { props["cclom:location"] = mutableListOf(it) }
        wwwUrl?.let { props["ccm:wwwurl"] = mutableListOf(it) }
        ltiUrl?.let { props["virtual:ltiurl"] = mutableListOf(it) }
        connectorRenderUrl?.let { props["virtual:connectorrenderurl"] = mutableListOf(it) }
        val n = Node().mimetype(mimetype).properties(props)
        mediatype?.let { n.mediatype(it) }
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
    fun frontendRemoteRepositoryTypeIsRejected() {
        val n = node().remote(Remote().repository(Repo().repositoryType("YOUTUBE")))
        assertThrows<ObjectTypeNotSupportedException> {
            underTest.getRenderModule<RenderModule>(n)
        }
    }

    @Test
    fun nonFrontendRemoteRepositoryTypeStillResolves() {
        val n = node().remote(Remote().repository(Repo().repositoryType("ALFRESCO")))
        assertSame(image, underTest.getRenderModule<RenderModule>(n))
    }

    @Test
    fun wwwUrlNodeWithNonExemptModuleIsRejected() {
        // resolves to IMAGE via mimetype, but the wwwurl marks it as a frontend-rendered link node
        assertThrows<ObjectTypeNotSupportedException> {
            underTest.getRenderModule<RenderModule>(node(wwwUrl = "https://example.org/some-page"))
        }
    }

    @Test
    fun wwwUrlNodeResolvingToSodixStillRenders() {
        val resolved = underTest.getRenderModule<RenderModule>(
            node(replicationSource = "SODIX", wwwUrl = "https://example.org/some-page")
        )
        assertSame(sodix, resolved)
    }

    @Test
    fun wwwUrlNodeResolvingToBinderStillRenders() {
        val resolved = underTest.getRenderModule<RenderModule>(
            node(resourceType = "git-binder", wwwUrl = "https://github.com/org/repo")
        )
        assertSame(binder, resolved)
    }

    @Test
    fun ltiUrlNodeIsRejected() {
        // resolves to IMAGE via mimetype, but the simple connector property marks it as frontend-rendered
        assertThrows<ObjectTypeNotSupportedException> {
            underTest.getRenderModule<RenderModule>(node(ltiUrl = "https://example.org/lti/launch"))
        }
    }

    @Test
    fun connectorRenderUrlNodeIsRejected() {
        assertThrows<ObjectTypeNotSupportedException> {
            underTest.getRenderModule<RenderModule>(node(connectorRenderUrl = "https://example.org/connector/render"))
        }
    }

    @Test
    fun simpleConnectorPropertyIsRejectedEvenForOtherwiseExemptModules() {
        // unlike ccm:wwwurl there is no module exemption: SODIX is rejected as well
        assertThrows<ObjectTypeNotSupportedException> {
            underTest.getRenderModule<RenderModule>(
                node(replicationSource = "SODIX", ltiUrl = "https://example.org/lti/launch")
            )
        }
    }

    @Test
    fun blankSimpleConnectorPropertyIsIgnored() {
        assertSame(image, underTest.getRenderModule<RenderModule>(node(ltiUrl = "")))
    }

    @Test
    fun nonOptInReplicationSourceModuleIsNotSkippedOnLocalContent() {
        // SODIX does not opt into fall-through: even with local content it still resolves to SODIX
        val resolved = underTest.getRenderModule<RenderModule>(
            node(replicationSource = "SODIX", location = null, hash = "abc123")
        )
        assertSame(sodix, resolved)
    }

    @Test
    fun unactivatedOptionalModuleIsSkippedAndFallsThroughToNextCandidate() {
        // Node matches JUPYTER_TYPE by mediatype, but the repo has not activated it => skip it
        // and fall through to the mimetype-prefix candidate (IMAGE, non-optional).
        val resolved = underTest.getRenderModule<RenderModule>(
            node(mediatype = "file-jupyter", mimetype = "image/png"),
            onlyActivated() // nothing activated
        )
        assertSame(image, resolved)
    }

    @Test
    fun activatedOptionalModuleStillResolves() {
        // Same node, but JUPYTER_TYPE is activated for the repo => it wins on the type branch.
        val resolved = underTest.getRenderModule<RenderModule>(
            node(mediatype = "file-jupyter", mimetype = "image/png"),
            onlyActivated("JUPYTER_TYPE")
        )
        assertSame(jupyterByType, resolved)
    }

    @Test
    fun optionalModuleWithoutFallbackThrowsWhenNotActivated() {
        // A .ipynb node's only candidate is the optional JUPYTER_MIME (exact mimetype); when not
        // activated it must never match => ObjectTypeNotSupportedException.
        assertThrows<ObjectTypeNotSupportedException> {
            underTest.getRenderModule<RenderModule>(
                node(mimetype = "application/x-ipynb+json"),
                onlyActivated() // nothing activated
            )
        }
    }

    @Test
    fun optionalModuleResolvesByExactMimetypeWhenActivated() {
        val resolved = underTest.getRenderModule<RenderModule>(
            node(mimetype = "application/x-ipynb+json"),
            onlyActivated("JUPYTER_MIME")
        )
        assertSame(jupyterByMime, resolved)
    }

    @Test
    fun defaultFilterResolvesOptionalModuleWithoutGating() {
        // The default (no-arg) overload keeps the previous repo-agnostic behavior for other callers.
        val resolved = underTest.getRenderModule<RenderModule>(
            node(mimetype = "application/x-ipynb+json")
        )
        assertSame(jupyterByMime, resolved)
    }
}
