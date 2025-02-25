package org.edu_sharing.rendering.modules.jupyter

import io.mockk.mockk

class JupyterRenderModuleTypeMapperTest {

    private val underTest = JupyterRenderModuleTypeMapper(mockk())

    /*@Test
    fun testModuleTypeAssociationsReturnsDefinitionWithMimetypePrefix() {
        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        assert(result.size == 1)
        assert(result[0].first.type == "file-jupyter")
        assert(result[0].first.mimeTypeSuffix == null)
        assert(result[0].first.mimeTypePrefix == null)
    }*/
}