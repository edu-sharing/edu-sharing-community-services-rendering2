package org.edu_sharing.rendering.modules.noConversion

import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PdfRenderModule(
    @Value("\${app.session.pdf.nodePermissionExpirationTime}")
    nodePermissionExpirationTime: Long?,
    mapper: Mapper,
    storageService: StorageService,
    mainJobCreationService: MainJobCreationService
    ) : BaseNoConversionModule(nodePermissionExpirationTime, mapper, storageService, mainJobCreationService) {

    override fun module() = RenderModules.PDF
}
