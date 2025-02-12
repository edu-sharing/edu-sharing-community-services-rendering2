package org.edu_sharing.rendering.modules.noConversion

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoConversionModuleTypeMapperTest {

    private val pdfModuleMock = mockk<PdfRenderModule>()
    private val htmlModuleMock = mockk<HtmlRenderModule>()
    private val underTest = NoConversionModuleTypeMapper(
        pdfRenderModule = pdfModuleMock,
        htmlRenderModule = htmlModuleMock
    )

    @Test
    fun testModuleTypeAssociationsReturnsProperTypeMappingForPdf() {
        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        assertTrue(result.size == 2, "Expected 2 module type definitions in result list, got ${result.size}")
        val pdfMapping = result.firstOrNull {it.second == pdfModuleMock}
        assertNotNull(pdfMapping, "Pdf mapping not found in mappings list")
        if (pdfMapping != null) {
            assertTrue(pdfMapping.first.type == null)
            assertTrue(pdfMapping.first.mimeTypePrefix == "application")
            assertTrue(pdfMapping.first.mimeTypeSuffix == "pdf")
        }
    }

    @Test
    fun testModuleTypeAssociationsReturnsProperTypeMappingForHtml() {
        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        assertTrue(result.size == 2, "Expected 2 module type definitions in result list, got ${result.size}")
        val htmlMapping = result.firstOrNull {it.second == htmlModuleMock}
        assertNotNull(htmlMapping, "Html mapping not found in mappings list")
        if (htmlMapping != null) {
            assertTrue(htmlMapping.first.type == null)
            assertTrue(htmlMapping.first.mimeTypePrefix == "text")
            assertTrue(htmlMapping.first.mimeTypeSuffix == "html")
        }
    }


}