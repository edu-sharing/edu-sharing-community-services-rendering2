package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.springframework.stereotype.Component

@Component
class H5pRenderModuleTypeMapper(
    private val h5pRenderModule: H5pRenderModule,
) : ModuleTypeMapper {

    override fun moduleTypeAssociations() = listOf(ModuleTypeDefinition(type = "file-h5p") to h5pRenderModule)
}
