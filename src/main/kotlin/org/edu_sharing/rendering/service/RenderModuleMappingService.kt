package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.RenderModules
import org.springframework.stereotype.Service

@Service
class RenderModuleMappingService {
    fun getModule(mimeType: String): RenderModules {
        return when (mimeType.substringBefore("/")) {
            "audio" -> RenderModules.AUDIO
            "video" -> RenderModules.VIDEO
            else -> RenderModules.IMAGE
        }
    }
}