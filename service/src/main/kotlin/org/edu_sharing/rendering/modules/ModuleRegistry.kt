package org.edu_sharing.rendering.modules

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.exception.ModuleNotRegisteredException
import org.edu_sharing.rendering.core.exception.ObjectTypeNotSupportedException
import org.springframework.lang.Nullable
import org.springframework.stereotype.Component

@Component
class ModuleRegistry(@Nullable private val moduleTypeMapper: List<ModuleTypeMapper>) {
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

    init {
        moduleTypeMapper.forEach { mapper ->
            mapper.moduleTypeAssociations().forEach { (typeDefinition, mapper) ->
                if (typeDefinition.type != null) {
                    modulesByType[typeDefinition.type] = mapper
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
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : RenderModule> getRenderModule(moduleName: String): T {
        val result = this.modulesByName[moduleName] ?: throw ModuleNotRegisteredException(moduleName)
        return result as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : RenderModule> getRenderModule(type: String, mimeType: String, replicationSource: String?, resourceType: String?): T {
        val result = modulesByType[type]
            ?: modulesByReplicationSource[replicationSource ?: ""]
            ?: modulesByResourceType[resourceType ?: ""]
            ?: moduleByMimeType[mimeType]
            ?: modulesByMimeTypePrefix[mimeType.substringBefore("/")]
            ?: throw ObjectTypeNotSupportedException()
        return result as T
    }

    fun <T: RenderModule> getRenderModule(cacheObject: CacheObject): T {
        return getRenderModule(
            type = cacheObject.type,
            mimeType = cacheObject.mimeType,
            replicationSource = null,
            resourceType = null
        )
    }

    fun <T: RenderModule> getRenderModule(node: Node): T {
        return getRenderModule(
            type = node.mediatype,
            mimeType = node.mimetype,
            replicationSource = node.properties.getOrDefault("ccm:replicationSource", mutableListOf(""))[0],
            resourceType = node.properties.getOrDefault("ccm:ccressourcetype", mutableListOf(""))[0]
        )
    }

    fun getModuleTypeMapperList() = moduleTypeMapper
}
