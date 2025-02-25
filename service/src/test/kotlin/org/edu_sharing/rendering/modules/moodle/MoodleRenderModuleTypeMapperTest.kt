package org.edu_sharing.rendering.modules.moodle

import io.mockk.mockk

class MoodleRenderModuleTypeMapperTest {

    private val underTest = MoodleRenderModuleTypeMapper(mockk())

    /*@Test
    fun testModuleTypeAssociationsReturnsProperTypeMapping() {
        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        assertTrue(result.size == 1, "Expected 1 module type definition in result list, got ${result.size}")
        assertTrue(result[0].first.type == "file-moodle")
        assertTrue(result[0].first.mimeTypePrefix == null)
        assertTrue(result[0].first.mimeTypeSuffix == null)
        assertTrue(result[0].second == underTest)
    }*/
}