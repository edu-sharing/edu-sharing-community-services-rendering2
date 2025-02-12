package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.springframework.stereotype.Component

@Component
class EduHtmlRenderModuleTypeMapper(
    private val eduHtmlRenderModule: EduHtmlRenderModule
) : ModuleTypeMapper {

    override fun moduleTypeAssociations() =
        listOf(ModuleTypeDefinition(resourceType = "eduhtml") to eduHtmlRenderModule)
}
