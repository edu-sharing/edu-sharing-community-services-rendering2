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
        val DOC = "application" to "msword"
        val DOCX = "application" to "vnd.openxmlformats-officedocument.wordprocessingml.document"
        val PPT = "application" to "vnd.ms-powerpoint"
        val PPTX = "application" to "vnd.openxmlformats-officedocument.presentationml.presentation"
        val XLS = "application" to "vnd.ms-excel"
        val XLSX = "application" to "vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        val ODT = "application" to "vnd.oasis.opendocument.text"
        val ODP = "application" to "vnd.oasis.opendocument.presentation"
        val ODS = "application" to "vnd.oasis.opendocument.spreadsheet"
        val RTF = "application" to "rtf"
        val OTT = "application" to "vnd.oasis.opendocument.text-template"
        val CSV = "text" to "csv"
        val TXT = "text" to "plain"
    }

    override fun moduleTypeAssociations(): List<Pair<ModuleTypeDefinition, RenderModule>> {
        return listOf(
            ModuleTypeDefinition(null, DOC.first, DOC.second) to documentRenderModule,
            ModuleTypeDefinition(null, DOCX.first, DOCX.second) to documentRenderModule,
            ModuleTypeDefinition(null, PPT.first, PPT.second) to documentRenderModule,
            ModuleTypeDefinition(null, PPTX.first, PPTX.second) to documentRenderModule,
            ModuleTypeDefinition(null, ODT.first, ODT.second) to documentRenderModule,
            ModuleTypeDefinition(null, ODP.first, ODP.second) to documentRenderModule,
            ModuleTypeDefinition(null, TXT.first, TXT.second) to documentRenderModule,
            ModuleTypeDefinition(null, OTT.first, OTT.second) to documentRenderModule,
            ModuleTypeDefinition(null, RTF.first, RTF.second) to documentRenderModule,
            ModuleTypeDefinition(null, ODS.first, ODS.second) to (spreadsheetRenderModule ?: documentRenderModule),
            ModuleTypeDefinition(null, XLS.first, XLS.second) to (spreadsheetRenderModule ?: documentRenderModule),
            ModuleTypeDefinition(null, XLSX.first, XLSX.second) to (spreadsheetRenderModule ?: documentRenderModule),
            ModuleTypeDefinition(null, CSV.first, CSV.second) to (spreadsheetRenderModule ?: documentRenderModule)
        )
    }
}
