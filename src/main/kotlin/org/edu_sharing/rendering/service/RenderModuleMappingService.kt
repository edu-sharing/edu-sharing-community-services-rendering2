package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.RenderModules
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

@Service
class RenderModuleMappingService {
    fun getModule(type: String = "", mimeType: String = ""): RenderModules {
        if (type == "moodle") {
            return RenderModules.MOODLE
        }
        if (type == "scorm") {
            return RenderModules.SCORM
        }
        if (type == "eduhtml") {
            return RenderModules.EDUHTML
        }
        return when (mimeType.substringBefore("/")) {
            "audio" -> RenderModules.AUDIO
            "video" -> RenderModules.VIDEO
            "application" -> mapApplication(mimeType)
            "image" -> RenderModules.IMAGE
            else -> RenderModules.DEFAULT
        }
    }

    private fun mapApplication(mimeType: String): RenderModules {
        return when (mimeType) {
            MediaType.APPLICATION_PDF_VALUE -> RenderModules.PDF
            else -> RenderModules.DEFAULT
        }
    }
}