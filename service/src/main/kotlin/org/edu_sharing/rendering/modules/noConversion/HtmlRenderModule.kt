package org.edu_sharing.rendering.modules.noConversion

import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class HtmlRenderModule(
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    @Value("\${app.session.html.nodePermissionExpirationTime}")
    nodePermissionExpirationTime: Long?,
    mapper: Mapper,
    storageService: StorageService,
    mainJobCreationService: MainJobCreationService
) : BaseNoConversionModule(nodePermissionExpirationTime, mapper, storageService, mainJobCreationService) {
    override fun module() = "HTML"
    override fun isOptionalModule() = true
    override fun getCspHeader(repoId: String): String? {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Unknown repository id: $repoId") }
        return registration.module[module()]?.cspHeader
    }
}
