package org.edu_sharing.rendering.modules.spreadsheet

import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.document.DocumentRenderModule
import org.edu_sharing.rendering.modules.document.DocumentService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

@ConditionalOnProperty(name = ["edu_sharing.enable_spreadsheet_to_html"], havingValue = "true")
@Component
class SpreadsheetRenderModule(
    mapper: Mapper,
    documentService: DocumentService
): DocumentRenderModule(mapper = mapper, documentService = documentService) {
    override fun module() = RenderModules.SPREADSHEET
    override fun getTargetMimetype() = MediaType.TEXT_HTML_VALUE
}