package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.exception.ObjectTypeNotSupportedException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

@Service
class RenderModuleMappingService (
    @Value("\${app.converter.spreadsheetToHtml.enabled}")
    private val enableHtmlSpreadsheet: Boolean
){
    companion object {
        const val DOC = "application/msword"
        const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val PPT = "application/vnd.ms-powerpoint"
        const val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        const val XLS = "application/vnd.ms-excel"
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val ODT = "application/vnd.oasis.opendocument.text"
        const val ODP = "application/vnd.oasis.opendocument.presentation"
        const val ODS = "application/vnd.oasis.opendocument.spreadsheet"
    }

    fun getModule(type: String = "", mimeType: String = "") = when (type) {
        "moodle" -> RenderModules.MOODLE
        "scorm" -> RenderModules.SCORM
        "eduhtml" -> RenderModules.EDUHTML
        "h5p" -> RenderModules.H5P
        else -> getByMimeType(mimeType)
    }

    private fun getByMimeType(mimeType: String) = when (mimeType.substringBefore("/")) {
        "audio" -> RenderModules.AUDIO
        "video" -> RenderModules.VIDEO
        "image" -> RenderModules.IMAGE
        "application" -> mapApplication(mimeType)
        else -> throw ObjectTypeNotSupportedException()
    }

    private fun mapApplication(mimeType: String) = when (mimeType) {
        MediaType.APPLICATION_PDF_VALUE -> RenderModules.PDF
        ODS, XLSX, XLS -> if (enableHtmlSpreadsheet) RenderModules.SPREADSHEET else RenderModules.DOCUMENT
        DOC, DOCX, PPT, PPTX, ODT, ODP -> RenderModules.DOCUMENT
        else -> throw ObjectTypeNotSupportedException()
    }
}
