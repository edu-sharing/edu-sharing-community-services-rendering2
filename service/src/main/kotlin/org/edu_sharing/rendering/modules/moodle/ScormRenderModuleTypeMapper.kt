package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.springframework.stereotype.Component

@Component
class ScormRenderModuleTypeMapper(
    private val scormRenderModule: ScormRenderModule
): ModuleTypeMapper {
    override fun moduleTypeAssociations() = listOf(ModuleTypeDefinition(type = "file-scorm") to scormRenderModule)
}