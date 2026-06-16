package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
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
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    mapper: Mapper,
    documentService: DocumentService,
    subJobRepository: SubJobRepository,
    amqpTemplate: AmqpTemplate
): DocumentRenderModule(
    nodePermissionExpirationTime = nodePermissionExpirationTime,
    mapper = mapper,
    documentService = documentService,
    subJobRepository = subJobRepository,
    amqpTemplate = amqpTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun module() = "SPREADSHEET"
    override fun getTargetMimetype() = MediaType.TEXT_HTML_VALUE
    override fun isOptionalModule() = true
    override fun getCspHeader(repoId: String): String? {
        log.debug("getCspHeader: repoId=$repoId")
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Unknown repository id: $repoId") }
        return registration.module[module()]?.cspHeader
    }
}
