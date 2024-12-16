package org.edu_sharing.rendering.modules.av

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.av.audio.AudioRenderModule
import org.edu_sharing.rendering.modules.av.video.VideoRenderModule
import org.springframework.stereotype.Component

@Component
class AvModuleTypeMapper(
    private val audioRenderModule: AudioRenderModule,
    private val videoRenderModule: VideoRenderModule
) : ModuleTypeMapper {
    override fun moduleTypeAssociations(): List<Pair<ModuleTypeDefinition, RenderModule>> =
        listOf(
            ModuleTypeDefinition(mimeTypePrefix = "audio") to audioRenderModule,
            ModuleTypeDefinition(mimeTypePrefix = "video") to videoRenderModule
        )
}
