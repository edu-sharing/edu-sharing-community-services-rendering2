package org.edu_sharing.rendering.modules.binder

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.springframework.stereotype.Component

@Component
class BinderRenderModuleTypeMapper(
    private val module: BinderRenderModule
): ModuleTypeMapper {
    override fun moduleTypeAssociations() = listOf(ModuleTypeDefinition(resourceType = "git-binder") to module)
}