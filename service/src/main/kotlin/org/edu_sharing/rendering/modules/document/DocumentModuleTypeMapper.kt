package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.stereotype.Component

@Component
class DocumentModuleTypeMapper(
    private val documentRenderModule: DocumentRenderModule,
    private val spreadsheetRenderModule: SpreadsheetRenderModule?

) : ModuleTypeMapper {

    companion object {
        const val DOC = "msword"
        const val DOCX = "vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val PPT = "vnd.ms-powerpoint"
        const val PPTX = "vnd.openxmlformats-officedocument.presentationml.presentation"
        const val XLS = "vnd.ms-excel"
        const val XLSX = "vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val ODT = "vnd.oasis.opendocument.text"
        const val ODP = "vnd.oasis.opendocument.presentation"
        const val ODS = "vnd.oasis.opendocument.spreadsheet"
        const val RTF = "rtf"
        const val OTT = "vnd.oasis.opendocument.text-template"
    }

    override fun moduleTypeAssociations(): List<Pair<ModuleTypeDefinition, RenderModule>> {
        return listOf(
            ModuleTypeDefinition(null, "application", DOC) to documentRenderModule,
            ModuleTypeDefinition(null, "application", DOCX) to documentRenderModule,
            ModuleTypeDefinition(null, "application", PPT) to documentRenderModule,
            ModuleTypeDefinition(null, "application", PPTX) to documentRenderModule,
            ModuleTypeDefinition(null, "application", ODT) to documentRenderModule,
            ModuleTypeDefinition(null, "application", ODP) to documentRenderModule,
            ModuleTypeDefinition(null, "text", "plain") to documentRenderModule,
            ModuleTypeDefinition(null, "application", OTT) to documentRenderModule,
            ModuleTypeDefinition(null, "application", RTF) to documentRenderModule,
            ModuleTypeDefinition(null, "application", ODS) to (spreadsheetRenderModule ?: documentRenderModule),
            ModuleTypeDefinition(null, "application", XLS) to (spreadsheetRenderModule ?: documentRenderModule),
            ModuleTypeDefinition(null, "application", XLSX) to (spreadsheetRenderModule ?: documentRenderModule),
        )
    }
}
