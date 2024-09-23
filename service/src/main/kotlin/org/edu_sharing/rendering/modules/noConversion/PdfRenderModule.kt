package org.edu_sharing.rendering.modules.noConversion

import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.storage.StorageService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PdfRenderModule(
    @Value("\${app.session.pdf.nodePermissionExpirationTime}")
    nodePermissionExpirationTime: Long?,
    mapper: Mapper,
    storageService: StorageService,
    mainJobCreationService: MainJobCreationService
    ) : BaseNoConversionModule(nodePermissionExpirationTime, mapper, storageService, mainJobCreationService), ModuleTypeMapper {

    override fun module() = "PDF"
    override fun moduleTypeAssociations() =
        listOf(ModuleTypeDefinition(mimeTypePrefix = "application", mimeTypeSuffix = "pdf") to this)
}
