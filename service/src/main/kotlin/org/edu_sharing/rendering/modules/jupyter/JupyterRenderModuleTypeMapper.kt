package org.edu_sharing.rendering.modules.jupyter

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.springframework.stereotype.Component

@Component
class JupyterRenderModuleTypeMapper(
    private val jupyterRenderModule: JupyterRenderModule ,
) :  ModuleTypeMapper {

    override fun moduleTypeAssociations() = listOf(ModuleTypeDefinition(type = "file-jupyter") to jupyterRenderModule)

}
