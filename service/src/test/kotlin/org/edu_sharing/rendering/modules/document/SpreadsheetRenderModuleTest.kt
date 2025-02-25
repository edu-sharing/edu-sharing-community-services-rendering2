package org.edu_sharing.rendering.modules.document

import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.core.AmqpTemplate

@ExtendWith(MockKExtension::class)
class SpreadsheetRenderModuleTest {
    private val mapper = mockk<Mapper>()
    private val documentService = mockk<DocumentService>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val amqpTemplate = mockk<AmqpTemplate>()
    private val nodeExpiration = 88L

    lateinit var underTest: SpreadsheetRenderModule

    /*@BeforeEach
    fun setup() {
        underTest = SpreadsheetRenderModule(
            nodePermissionExpirationTime = nodeExpiration,
            mapper = mapper,
            documentService = documentService,
            amqpTemplate = amqpTemplate,
            subJobRepository = subJobRepository,
        )
    }

    @Test
    fun testModuleReturnsSpreadsheetModule() {
        assert(underTest.module() == "SPREADSHEET")
    }

    @Test
    fun testGetNodeExpirationTimeReturnsSetTime() {
        assert(underTest.getNodePermissionExpirationTime() == nodeExpiration)
    }

    @Test
    fun testGetTargetMimeTypeReturnsHtml() {
        assert(underTest.getTargetMimetype() == MediaType.TEXT_HTML_VALUE)
    }*/
}