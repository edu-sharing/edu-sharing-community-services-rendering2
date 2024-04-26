package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.RenderModules
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

@Service
class RenderModuleMappingService {
    fun getModule(type: String = "", mimeType: String = "") = when (type) {
        "moodle" -> RenderModules.MOODLE
        "scorm" -> RenderModules.SCORM
        "eduhtml" -> RenderModules.EDUHTML
        else -> getByMimeType(mimeType)
    }
    

    private fun getByMimeType(mimeType: String) = when (mimeType.substringBefore("/")) {
        "audio" -> RenderModules.AUDIO
        "video" -> RenderModules.VIDEO
        "image" -> RenderModules.IMAGE
        "application" -> mapApplication(mimeType)
        else -> RenderModules.DEFAULT
    }

    private fun mapApplication(mimeType: String) = when (mimeType) {
        MediaType.APPLICATION_PDF_VALUE -> RenderModules.PDF
        else -> RenderModules.DEFAULT
    }

}
