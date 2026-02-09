package org.edu_sharing.rendering.modules.document

import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class DocumentModuleTypeMapperTest {

    private val documentModule = mockk<DocumentRenderModule>()

    private val spreadsheetModule =  mockk<SpreadsheetRenderModule>()

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
        val underTest = DocumentModuleTypeMapper(documentModule, null)

        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        assertEquals(result.size, EXPECTED_MAPPING_AMOUNT, "Incorrect number of mappings returned")

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
        val underTest = DocumentModuleTypeMapper(documentModule, spreadsheetModule)

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
}
