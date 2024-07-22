package org.edu_sharing.rendering.modules.spreadsheet

import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.document.DocumentService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.MediaType

@ExtendWith(MockKExtension::class)
class SpreadsheetRenderModuleTest {
    private val mapper = mockk<Mapper>()
    private val documentService = mockk<DocumentService>()
    private val nodeExpiration = 88L

    lateinit var underTest: SpreadsheetRenderModule

    @BeforeEach
    fun setup() {
        underTest = SpreadsheetRenderModule(
            nodeExpiration, mapper, documentService,
        )
    }

    @Test
    fun testModuleReturnsSpreadsheetModule() {
        assert(underTest.module() == RenderModules.SPREADSHEET)
    }

    @Test
    fun testGetNodeExpirationTimeReturnsSetTime() {
        assert(underTest.getNodePermissionExpirationTime() == nodeExpiration)
    }

    @Test
    fun testGetTargetMimeTypeReturnsHtml() {
        assert(underTest.getTargetMimetype() == MediaType.TEXT_HTML_VALUE)
    }
}