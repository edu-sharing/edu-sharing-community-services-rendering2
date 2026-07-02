package org.edu_sharing.rendering.modules.omega

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.springframework.stereotype.Component

@Component
class OmegaRenderModuleTypeMapper(
    private val module: OmegaRenderModule
) : ModuleTypeMapper {
    override fun moduleTypeAssociations() = listOf(ModuleTypeDefinition(replicationSource = "DE.FWU") to module)
}
