package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.springframework.stereotype.Component

@Component
class MoodleRenderModuleTypeMapper(
    private val moodleRenderModule: MoodleRenderModule
) : ModuleTypeMapper {
    override fun moduleTypeAssociations() = listOf(ModuleTypeDefinition(resourceType = "moodle") to moodleRenderModule)
}
