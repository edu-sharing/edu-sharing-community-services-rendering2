package org.edu_sharing.rendering.modules.noConversion

import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class HtmlRenderModule(
    @Value("\${app.session.html.nodePermissionExpirationTime}")
    nodePermissionExpirationTime: Long?,
    mapper: Mapper,
    storageService: StorageService,
    mainJobCreationService: MainJobCreationService
) : BaseNoConversionModule(nodePermissionExpirationTime, mapper, storageService, mainJobCreationService) {

    override fun module() = RenderModules.HTML
}
