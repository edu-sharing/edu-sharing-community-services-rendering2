package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["app.converter.spreadsheetToHtml.enabled"], havingValue = "true")
class SpreadsheetRenderModule(
    @Value("\${app.session.spreadsheet.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    mapper: Mapper,
    documentService: DocumentService,
    subJobRepository: SubJobRepository,
    amqpTemplate: AmqpTemplate
): DocumentRenderModule(
    nodePermissionExpirationTime = nodePermissionExpirationTime,
    mapper = mapper,
    documentService = documentService,
    subJobRepository = subJobRepository,
    amqpTemplate = amqpTemplate
) {
    override fun module() = "SPREADSHEET"
    override fun getTargetMimetype() = MediaType.TEXT_HTML_VALUE
}
