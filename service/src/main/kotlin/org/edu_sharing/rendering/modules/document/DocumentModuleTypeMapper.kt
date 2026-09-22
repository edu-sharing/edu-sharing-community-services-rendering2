package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.modules.ModuleTypeDefinition
import org.edu_sharing.rendering.modules.ModuleTypeMapper
import org.edu_sharing.rendering.modules.RenderModule
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Registers the document mimetypes. Which of them are registered is driven by
 * `app.converter.document.extensions`, whose vocabulary mirrors the document-converter's own
 * `app.supportedExtensions`. A type that is not registered is not rendered by this service at all -
 * the client falls back to its own default rendering.
 */
@Component
class DocumentModuleTypeMapper(
    private val documentRenderModule: DocumentRenderModule,
    private val spreadsheetRenderModule: SpreadsheetRenderModule?,
    @param:Value($$"${app.converter.document.extensions:}")
    private val configuredExtensions: List<String>

) : ModuleTypeMapper {

    // Instance properties on purpose: a test reflects over the companion object below and asserts it
    // holds nothing but the 13 mimetypes.
    private val log = LoggerFactory.getLogger(javaClass)

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

    /** The accepted vocabulary of `app.converter.document.extensions`, in registration order. */
    private val mimeTypesByExtension: Map<String, Pair<String, String>> = linkedMapOf(
        "doc" to DOC,
        "docx" to DOCX,
        "ppt" to PPT,
        "pptx" to PPTX,
        "odt" to ODT,
        "odp" to ODP,
        "txt" to TXT,
        "ott" to OTT,
        "rtf" to RTF,
        "ods" to ODS,
        "xls" to XLS,
        "xlsx" to XLSX,
        "csv" to CSV
    )

    /** Extensions the spreadsheet module handles, as long as spreadsheet-to-html is enabled. */
    private val spreadsheetExtensions = setOf("ods", "xls", "xlsx", "csv")

    override fun moduleTypeAssociations(): List<Pair<ModuleTypeDefinition, RenderModule>> {
        val enabledExtensions = resolveEnabledExtensions()
        val associations = mimeTypesByExtension
            .filterKeys { enabledExtensions.contains(it) }
            .map { (extension, mimeType) ->
                val module = if (spreadsheetExtensions.contains(extension)) {
                    spreadsheetRenderModule ?: documentRenderModule
                } else {
                    documentRenderModule
                }
                ModuleTypeDefinition(null, mimeType.first, mimeType.second) to module
            }
        warnAboutModulesWithoutExtensions(associations)
        return associations
    }

    /**
     * Normalizes the configured extensions - trimmed, lower case, a leading dot tolerated - and drops
     * unknown ones with a warning instead of failing the startup. An empty configuration registers
     * every extension: compose and helm always render the property key, so an unset
     * `${RENDERING2_...:-}` env var reaches us as an empty string and has to behave exactly like an
     * absent key (see commit 7be037c8 for the same class of bug on module credentials).
     */
    private fun resolveEnabledExtensions(): Set<String> {
        val normalized = configuredExtensions
            .map { it.trim().removePrefix(".").lowercase() }
            .filter { it.isNotEmpty() }

        if (normalized.isEmpty()) {
            return mimeTypesByExtension.keys
        }

        val (known, unknown) = normalized.partition { mimeTypesByExtension.containsKey(it) }
        if (unknown.isNotEmpty()) {
            log.warn(
                "Ignoring unknown document extension(s): {}. Known extensions: {}",
                unknown.joinToString(","), mimeTypesByExtension.keys.joinToString(",")
            )
        }
        if (known.isEmpty()) {
            log.warn(
                "app.converter.document.extensions names no known extension, so no document type is " +
                    "rendered by this service at all. Known extensions: {}",
                mimeTypesByExtension.keys.joinToString(",")
            )
        } else {
            log.info("Registering document extensions: {}", known.joinToString(","))
        }
        return known.toSet()
    }

    /**
     * A module left without a single extension handles no node at all. That is a legitimate
     * configuration - "only convert odt", say - but worth a line in the log, because nothing else
     * makes it visible.
     */
    private fun warnAboutModulesWithoutExtensions(
        associations: List<Pair<ModuleTypeDefinition, RenderModule>>
    ) {
        val registeredModules = associations.map { it.second }.toSet()
        listOfNotNull(documentRenderModule, spreadsheetRenderModule)
            .filterNot { registeredModules.contains(it) }
            .forEach {
                log.warn(
                    "No extension is configured for the {} module - it will not render any node",
                    it.module()
                )
            }
    }
}
