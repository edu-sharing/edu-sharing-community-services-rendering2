package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["edu_sharing.enable_spreadsheet_to_html"], havingValue = "true")
class SpreadsheetRenderModule(
    @Value("\${app.session.spreadsheet.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    mapper: Mapper,
    documentService: DocumentService
): DocumentRenderModule(nodePermissionExpirationTime, mapper = mapper, documentService = documentService) {
    override fun module() = RenderModules.SPREADSHEET
    override fun getTargetMimetype() = MediaType.TEXT_HTML_VALUE
}
