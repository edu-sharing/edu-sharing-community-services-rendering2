package org.edu_sharing.rendering.modules.image

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ImageRenderModuleTypeMapper(
    @Value("\${app.session.image.nodePermissionExpirationTime}")
    private val imageRenderModule: ImageRenderModule,
) : ModuleTypeMapper {


    override fun moduleTypeAssociations() =
        listOf(ModuleTypeDefinition(mimeTypePrefix = "image") to imageRenderModule)
}
