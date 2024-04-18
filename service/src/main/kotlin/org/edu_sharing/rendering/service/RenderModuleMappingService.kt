package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderModules
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

@Service
class RenderModuleMappingService {
    fun getModule(request: RenderDataRequest): RenderModules {
        if (request.type == "moodle") {
            return RenderModules.MOODLE
        }
        if (request.type == "scorm") {
            return RenderModules.SCORM
        }
        return when (request.mimeType.substringBefore("/")) {
            "audio" -> RenderModules.AUDIO
            "video" -> RenderModules.VIDEO
            "application" -> mapApplication(request.mimeType)
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