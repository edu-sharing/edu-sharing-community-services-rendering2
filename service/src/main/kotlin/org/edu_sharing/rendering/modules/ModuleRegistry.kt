package org.edu_sharing.rendering.modules

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.exception.ModuleNotRegisteredException
import org.edu_sharing.rendering.core.exception.ObjectTypeNotSupportedException
import org.slf4j.LoggerFactory
import org.springframework.lang.Nullable
import org.springframework.stereotype.Component

@Component
class ModuleRegistry(@Nullable private val moduleTypeMapper: List<ModuleTypeMapper>) {
    private val log = LoggerFactory.getLogger(javaClass)
    private final val modulesByName: Map<String, RenderModule> = moduleTypeMapper
        .flatMap { it.moduleTypeAssociations() }
        .map { it.second }
        .distinct()
        .associateBy { it.module() }

    private final val modulesByType: MutableMap<String, RenderModule> = mutableMapOf()
    private final val moduleByMimeType: MutableMap<String, RenderModule> = mutableMapOf()
    private final val modulesByMimeTypePrefix: MutableMap<String, RenderModule> = mutableMapOf()
    private final val modulesByReplicationSource: MutableMap<String, RenderModule> = mutableMapOf()
    private final val modulesByResourceType: MutableMap<String, RenderModule> = mutableMapOf()
    private final val modulesByRemoteRepositoryType: MutableMap<String, RenderModule> = mutableMapOf()

    companion object {
        private val FRONTEND_REMOTE_REPOSITORY_TYPES = setOf(
            "PIXABAY",
            "YOUTUBE",
            "LEARNINGAPPS",
            "OERSI",
            "BROCKHAUS",
        )
        private val WWWURL_EXEMPT_MODULES = setOf(
            "SODIX",
            "BINDER",
            "OMEGA"
        )
    }

    init {
        moduleTypeMapper.forEach { mapper ->
            mapper.moduleTypeAssociations().forEach { (typeDefinition, mapper) ->
                if (typeDefinition.type != null) {
                    modulesByType[typeDefinition.type] = mapper
                }else if (typeDefinition.remoteRepositoryType != null) {
                    modulesByRemoteRepositoryType[typeDefinition.remoteRepositoryType] = mapper
                } else if (typeDefinition.replicationSource != null) {
                    modulesByReplicationSource[typeDefinition.replicationSource] = mapper
                } else if (typeDefinition.resourceType != null) {
                    modulesByResourceType[typeDefinition.resourceType] = mapper
                } else if (typeDefinition.mimeTypePrefix != null) {
                    if (typeDefinition.mimeTypeSuffix != null) {
                        moduleByMimeType["${typeDefinition.mimeTypePrefix}/${typeDefinition.mimeTypeSuffix}"] =
                            mapper
                    } else {
                        modulesByMimeTypePrefix[typeDefinition.mimeTypePrefix] = mapper
                    }
                }
            }
        }
        log.debug(
            "Module index built: byName={}, byType={}, byRemoteRepositoryType={}, byReplicationSource={}, " +
                "byResourceType={}, byMimeType={}, byMimeTypePrefix={}",
            modulesByName.size, modulesByType.size, modulesByRemoteRepositoryType.size,
            modulesByReplicationSource.size, modulesByResourceType.size,
            moduleByMimeType.size, modulesByMimeTypePrefix.size
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : RenderModule> getRenderModule(moduleName: String): T {
        val result = this.modulesByName[moduleName] ?: throw ModuleNotRegisteredException(moduleName)
        log.debug("getRenderModule by name: resolved module '{}'", moduleName)
        return result as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : RenderModule> getRenderModule(
        type: String,
        mimeType: String,
        replicationSource: String?,
        resourceType: String?,
        remoteRepositoryType: String?,
        hasLocalContent: Boolean,
        moduleFilter: (RenderModule) -> Boolean
    ): T {
        log.debug(
            "getRenderModule lookup: type='{}', mimeType='{}', replicationSource='{}', resourceType='{}', remoteRepositoryType='{}', hasLocalContent={}",
            type, mimeType, replicationSource, resourceType, remoteRepositoryType, hasLocalContent
        )
        val result = modulesByType[type]?.takeIf(moduleFilter)?.also { log.debug("Resolved module '{}' via type='{}'", it.module(), type) }
            ?: modulesByRemoteRepositoryType[remoteRepositoryType ?: ""]?.takeIf(moduleFilter)?.also { log.debug("Resolved module '{}' via remoteRepositoryType='{}'", it.module(), remoteRepositoryType) }
            ?: modulesByReplicationSource[replicationSource ?: ""]
                ?.takeUnless { hasLocalContent && it.fallsThroughOnLocalContent() }
                ?.takeIf(moduleFilter)
                ?.also { log.debug("Resolved module '{}' via replicationSource='{}'", it.module(), replicationSource) }
            ?: modulesByResourceType[resourceType ?: ""]?.takeIf(moduleFilter)?.also { log.debug("Resolved module '{}' via resourceType='{}'", it.module(), resourceType) }
            ?: moduleByMimeType[mimeType]?.takeIf(moduleFilter)?.also { log.debug("Resolved module '{}' via mimeType='{}'", it.module(), mimeType) }
            ?: modulesByMimeTypePrefix[mimeType.substringBefore("/")]?.takeIf(moduleFilter)?.also { log.debug("Resolved module '{}' via mimeTypePrefix='{}'", it.module(), mimeType.substringBefore("/")) }
            ?: throw ObjectTypeNotSupportedException()
        return result as T
    }

    /**
     * Nodes from frontend-only remote repositories (pixabay, youtube, …) are rendered
     * client-side and are intentionally never registered here. Such repositories have no
     * public key in the repository config, so signature verification for them cannot
     * succeed — callers must reject these nodes before that point.
     */
    fun isFrontendRemoteRepository(node: Node): Boolean {
        val remoteType = node.remote?.repository?.repositoryType ?: return false
        return remoteType in FRONTEND_REMOTE_REPOSITORY_TYPES
    }

    fun <T: RenderModule> getRenderModule(node: Node, moduleFilter: (RenderModule) -> Boolean = { true }): T {
        if (isFrontendRemoteRepository(node)) {
            log.debug(
                "Node is from remote frontend repository (type='{}'), rendering done in frontend only",
                node.remote?.repository?.repositoryType
            )
            throw ObjectTypeNotSupportedException()
        }
        val location = node.properties?.getOrDefault("cclom:location", mutableListOf(""))[0]
        val hasLocalContent = location.isNullOrBlank() && !node.content?.hash.isNullOrBlank()
        val result = getRenderModule<T>(
            type = node.mediatype ?: "",
            mimeType = node.mimetype ?: "",
            replicationSource = node.properties?.getOrDefault("ccm:replicationsource", mutableListOf(""))[0],
            resourceType = node.properties?.getOrDefault("ccm:ccressourcetype", mutableListOf(""))[0],
            remoteRepositoryType = node.remote?.repository?.repositoryType,
            hasLocalContent = hasLocalContent,
            moduleFilter = moduleFilter
        )
        val wwwUrl = node.properties?.get("ccm:wwwurl")?.firstOrNull()
        if (!wwwUrl.isNullOrBlank() && WWWURL_EXEMPT_MODULES.none { it.equals(result.module(), ignoreCase = true) }) {
            log.debug("Node has ccm:wwwurl and resolved module '{}' is not exempt, rendering done in frontend only", result.module())
            throw ObjectTypeNotSupportedException()
        }
        return result
    }

    fun getModuleTypeMapperList() = moduleTypeMapper
}
