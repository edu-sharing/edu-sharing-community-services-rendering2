package org.edu_sharing.rendering.modules.h5p

import io.mockk.mockk

class H5pRenderModuleTypeMapperTest {

    private val module = mockk<H5pRenderModule>()

    private val underTest = H5pRenderModuleTypeMapper(module)

   /* @Test
    fun testModuleTypeAssociationsReturnsDefinitionWithMimetypePrefix() {
        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        assert(result.size == 1)
        assert(result[0].first.type == "file-h5p")
        assert(result[0].first.mimeTypeSuffix == null)
        assert(result[0].first.mimeTypePrefix == null)
        assert(result[0].second == underTest)
    }*/
}