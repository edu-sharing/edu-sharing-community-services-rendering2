package org.edu_sharing.rendering.modules.eduHtml

import io.mockk.mockk
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlRenderModule
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlRenderModuleTypeMapper
import org.junit.jupiter.api.Test

class EduHtmlRenderModuleTypeMapperTest {

    private val module = mockk<EduHtmlRenderModule>()
    private val underTest = EduHtmlRenderModuleTypeMapper(module)

    @Test
    fun testModuleTypeAssociationsReturnsDefinitionWithMimetypePrefix() {
        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        assert(result.size == 1)
        assert(result[0].first.resourceType == "eduhtml")
        assert(result[0].first.mimeTypeSuffix == null)
        assert(result[0].first.mimeTypePrefix == null)
        assert(result[0].first.replicationSource == null)
        assert(result[0].first.type == null)
        assert(result[0].first.remoteRepositoryType == null)

        assert(result[0].second == module)
    }
}
