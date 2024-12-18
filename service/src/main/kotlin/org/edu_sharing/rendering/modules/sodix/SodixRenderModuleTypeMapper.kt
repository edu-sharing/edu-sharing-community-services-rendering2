package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.springframework.stereotype.Component

@Component
class SodixRenderModuleTypeMapper(
    private val module: SodixRenderModule
) : ModuleTypeMapper {
    override fun moduleTypeAssociations() = listOf(ModuleTypeDefinition(replicationSource = "SODIX") to module)
}