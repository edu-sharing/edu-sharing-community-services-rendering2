package org.edu_sharing.rendering.modules.document

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.exception.ObjectTypeNotSupportedException
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.document.DocumentModuleTypeMapper.Companion.DOC
import org.edu_sharing.rendering.modules.document.DocumentModuleTypeMapper.Companion.DOCX
import org.edu_sharing.rendering.modules.document.DocumentModuleTypeMapper.Companion.PPT
import org.edu_sharing.rendering.modules.document.DocumentModuleTypeMapper.Companion.TXT
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class DocumentModuleTypeMapperTest {

    private val documentModule = mockk<DocumentRenderModule>()

    private val spreadsheetModule =  mockk<SpreadsheetRenderModule>()

    // The mapper names the module in its "no extension configured" warning.
    @BeforeEach
    fun stubModuleNames() {
        every { documentModule.module() } returns "DOCUMENT"
        every { spreadsheetModule.module() } returns "SPREADSHEET"
    }

    companion object {
        const val EXPECTED_MAPPING_AMOUNT = 13

        val supportedMimeTypes = setOf(
            "application" to "msword",
            "application" to "vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application" to "vnd.ms-powerpoint",
            "application" to "vnd.openxmlformats-officedocument.presentationml.presentation",
            "application" to "vnd.oasis.opendocument.text",
            "application" to "vnd.oasis.opendocument.presentation",
            "application" to "rtf",
            "application" to "vnd.oasis.opendocument.text-template",
            "text" to "plain"
        )

        val supportedMimeTypesSpreadsheet = setOf(
            "application" to "vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application" to "vnd.ms-excel",
            "application" to "vnd.oasis.opendocument.spreadsheet",
            "text" to "csv"
        )

        val allSupportedMimeTypes= supportedMimeTypes union supportedMimeTypesSpreadsheet
    }

    @Test
    fun testCompanionObjectContainsCorrectMimeTypeSuffixes() {
        val moduleCompanion = DocumentModuleTypeMapper::class.companionObject!!
        val companionProperties = moduleCompanion.memberProperties
        val companionInstance = DocumentModuleTypeMapper::class.companionObject!!.objectInstance!!

        val foundMimeTypes = companionProperties.map {
            it.getter.call(companionInstance)
        }

        assertEquals(allSupportedMimeTypes, foundMimeTypes.toSet())
    }

    @Test
    fun testModuleTypeAssociationsMapsAllMimeTypesToDocumentModuleIfNoSpreadsheetModuleProvided() {
        // Arrange
        val underTest = DocumentModuleTypeMapper(documentModule, null, emptyList())

        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        assertEquals(EXPECTED_MAPPING_AMOUNT, result.size, "Incorrect number of mappings returned")

        allSupportedMimeTypes.forEach {
            val foundPairs = result.filter {mapping ->
                mapping.first.mimeTypeSuffix == it.second && mapping.first.mimeTypePrefix == it.first && mapping.first.type == null
            }
            assert(foundPairs.size == 1)
            assert(foundPairs[0].second == documentModule)
        }
    }

    @Test
    fun testModuleTypeAssociationsMapsRespectiveMimeTypesToSpreadsheetModuleIfSpreadsheetModuleProvided() {
        // Arrange
        val underTest = DocumentModuleTypeMapper(documentModule, spreadsheetModule, emptyList())

        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        allSupportedMimeTypes.forEach {
            val foundPairs = result.filter {mapping ->
                mapping.first.mimeTypeSuffix == it.second && mapping.first.mimeTypePrefix == it.first && mapping.first.type == null
            }
            assert(foundPairs.size == 1)
            assert(foundPairs[0].second == if (supportedMimeTypesSpreadsheet.contains(it)) spreadsheetModule else documentModule )
        }
    }
    /**
     * Compose and helm always render the property key, so an unset env var arrives as a blank value.
     * That has to behave like an absent key and register everything - never disable the module.
     */
    @Test
    fun testBlankConfigurationRegistersAllExtensions() {
        // Spring splits on commas before binding, so a blank property arrives as no tokens or as
        // blank ones - never as a token containing a comma.
        listOf(emptyList(), listOf(""), listOf(" ", " "), listOf("  ")).forEach { configured ->
            val underTest = DocumentModuleTypeMapper(documentModule, spreadsheetModule, configured)

            assertEquals(
                EXPECTED_MAPPING_AMOUNT,
                underTest.moduleTypeAssociations().size,
                "Blank configuration $configured should register every extension"
            )
        }
    }

    @Test
    fun testOnlyConfiguredExtensionsAreRegistered() {
        val underTest = DocumentModuleTypeMapper(documentModule, spreadsheetModule, listOf("doc", "docx"))

        val result = underTest.moduleTypeAssociations()

        assertEquals(2, result.size)
        assertEquals(setOf(DOC, DOCX), result.map { it.first.mimeTypePrefix to it.first.mimeTypeSuffix }.toSet())
    }

    @Test
    fun testUnknownExtensionIsSkipped() {
        val underTest = DocumentModuleTypeMapper(documentModule, spreadsheetModule, listOf("doc", "docxx"))

        val result = underTest.moduleTypeAssociations()

        assertEquals(1, result.size)
        assertEquals(DOC, result[0].first.mimeTypePrefix to result[0].first.mimeTypeSuffix)
    }

    /** A list naming no known extension is taken literally: nothing is rendered by this service. */
    @Test
    fun testConfigurationWithoutAnyKnownExtensionRegistersNothing() {
        val underTest = DocumentModuleTypeMapper(documentModule, spreadsheetModule, listOf("pdf"))

        assertEquals(0, underTest.moduleTypeAssociations().size)
    }

    @Test
    fun testConfiguredExtensionsAreNormalized() {
        val underTest = DocumentModuleTypeMapper(documentModule, spreadsheetModule, listOf(" .DOCX ", "PPT", "txt"))

        val result = underTest.moduleTypeAssociations()

        assertEquals(3, result.size)
        assertEquals(
            setOf(DOCX, PPT, TXT),
            result.map { it.first.mimeTypePrefix to it.first.mimeTypeSuffix }.toSet()
        )
    }

    @Test
    fun testConfiguredSpreadsheetExtensionStillRoutesToSpreadsheetModule() {
        val withSpreadsheet = DocumentModuleTypeMapper(documentModule, spreadsheetModule, listOf("xls"))
        val withoutSpreadsheet = DocumentModuleTypeMapper(documentModule, null, listOf("xls"))

        assertEquals(spreadsheetModule, withSpreadsheet.moduleTypeAssociations().single().second)
        assertEquals(documentModule, withoutSpreadsheet.moduleTypeAssociations().single().second)
    }

    /** A filtered-out mimetype must stop resolving in the registry, which is what produces the 415. */
    @Test
    fun testFilteredMimeTypeNoLongerResolvesInRegistry() {
        val underTest = DocumentModuleTypeMapper(documentModule, spreadsheetModule, listOf("doc"))
        val registry = ModuleRegistry(listOf(underTest))

        assertEquals(documentModule, registry.getRenderModule(nodeWithMimeType("application/msword")))
        assertThrows<ObjectTypeNotSupportedException> {
            registry.getRenderModule<RenderModule>(nodeWithMimeType("application/vnd.ms-excel"))
        }
    }

    /**
     * With the shipped default both modules must stay resolvable by name - that lookup is what
     * RepositoryRegistrationService uses when it activates the optional SPREADSHEET module.
     */
    @Test
    fun testBothModulesAreResolvableByNameWithTheDefaultConfiguration() {
        val defaults = listOf("doc", "docx", "ppt", "pptx", "xls", "xlsx", "odt", "odp", "ods", "txt", "ott", "rtf", "csv")
        val registry = ModuleRegistry(listOf(DocumentModuleTypeMapper(documentModule, spreadsheetModule, defaults)))

        assertEquals(documentModule, registry.getRenderModule("DOCUMENT"))
        assertEquals(spreadsheetModule, registry.getRenderModule("SPREADSHEET"))
    }

    /**
     * A narrow list is the point of the feature, but it must not take a module down with it:
     * RegistrationRunner activates the optional SPREADSHEET module by name at startup, so an
     * unresolvable name used to abort the boot.
     */
    @Test
    fun testModuleStaysResolvableByNameWhenNoneOfItsExtensionsAreConfigured() {
        val mapper = DocumentModuleTypeMapper(documentModule, spreadsheetModule, listOf("odt"))
        val registry = ModuleRegistry(listOf(mapper), listOf(documentModule, spreadsheetModule))

        assertEquals(spreadsheetModule, registry.getRenderModule("SPREADSHEET"))
        assertEquals(documentModule, registry.getRenderModule("DOCUMENT"))
        // ... while no spreadsheet mimetype is dispatched any more
        assertThrows<ObjectTypeNotSupportedException> {
            registry.getRenderModule<RenderModule>(nodeWithMimeType("application/vnd.ms-excel"))
        }
    }

    private fun nodeWithMimeType(mimeType: String): Node =
        Node().mimetype(mimeType).properties(mutableMapOf())
}
