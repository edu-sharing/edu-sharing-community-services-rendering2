package org.edu_sharing.rendering.modules.ddb

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.springframework.stereotype.Component

@Component
class DdbRenderModuleTypeMapper(
    private val module: DdbRenderModule
): ModuleTypeMapper {
    override fun moduleTypeAssociations() = listOf(ModuleTypeDefinition(remoteRepositoryType = "DDB") to module)
}