package org.edu_sharing.rendering.modules.noConversion

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.stereotype.Component

@Component
class NoConversionModuleTypeMapper(
    private val pdfRenderModule: PdfRenderModule,
    private val htmlRenderModule: HtmlRenderModule
) : ModuleTypeMapper {
    override fun moduleTypeAssociations(): List<Pair<ModuleTypeDefinition, RenderModule>> =
        listOf(
            ModuleTypeDefinition(mimeTypePrefix = "application", mimeTypeSuffix = "pdf") to pdfRenderModule,
            ModuleTypeDefinition(mimeTypePrefix = "text", mimeTypeSuffix = "html") to htmlRenderModule
        )
}
