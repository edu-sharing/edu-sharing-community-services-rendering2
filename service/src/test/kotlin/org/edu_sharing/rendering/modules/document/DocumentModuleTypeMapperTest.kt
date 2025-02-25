package org.edu_sharing.rendering.modules.document

import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.memberProperties

@ExtendWith(MockKExtension::class)
class DocumentModuleTypeMapperTest {

    private val documentModule = mockk<DocumentRenderModule>()

    private val spreadsheetModule =  mockk<SpreadsheetRenderModule>()

    companion object {
        const val EXPECTED_MAPPING_AMOUNT = 9

        val supportedMimeTypeSuffixesDocument = setOf(
            "msword",
            "vnd.openxmlformats-officedocument.wordprocessingml.document",
            "vnd.ms-powerpoint",
            "vnd.openxmlformats-officedocument.presentationml.presentation",
            "vnd.oasis.opendocument.text",
            "vnd.oasis.opendocument.presentation",
        )

        val supportedMimeTypeSuffixesSpreadsheet = setOf(
            "vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "vnd.ms-excel",
            "vnd.oasis.opendocument.spreadsheet"
        )

        val allSupportedMimeTypeSuffixes = supportedMimeTypeSuffixesSpreadsheet union supportedMimeTypeSuffixesDocument
    }

    /*@Test
    fun testCompanionObjectContainsCorrectMimeTypeSuffixes() {
        val moduleCompanion = DocumentModuleTypeMapper::class.companionObject!!
        val companionProperties = moduleCompanion.memberProperties

        assertEquals(allSupportedMimeTypeSuffixes, companionProperties.map { it.getter.call(moduleCompanion) }.toSet())
    }

    @Test
    fun testModuleTypeAssociationsMapsAllMimeTypesToDocumentModuleIfNoSpreadsheetModuleProvided() {
        // Arrange
        val underTest = DocumentModuleTypeMapper(documentModule, null)

        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        assertTrue(result.size == EXPECTED_MAPPING_AMOUNT, "Incorrect number of mappings returned")

        allSupportedMimeTypeSuffixes.forEach {
            val foundPairs = result.filter {mapping ->
                mapping.first.mimeTypeSuffix == it && mapping.first.mimeTypePrefix == "application" && mapping.first.type == null
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
        allSupportedMimeTypeSuffixes.forEach {
            val foundPairs = result.filter {mapping ->
                mapping.first.mimeTypeSuffix == it && mapping.first.mimeTypePrefix == "application" && mapping.first.type == null
            }
            assert(foundPairs.size == 1)
            assert(foundPairs[0].second == if (supportedMimeTypeSuffixesSpreadsheet.contains(it)) spreadsheetModule else documentModule )
        }
    }*/
}