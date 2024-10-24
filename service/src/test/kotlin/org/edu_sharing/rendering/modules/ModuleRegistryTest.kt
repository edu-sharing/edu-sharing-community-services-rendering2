package org.edu_sharing.rendering.modules

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ModuleRegistryTest {
    private val moduleTypeMapperByType = mockk<ModuleTypeMapper>()
    private val moduleTypeMapperByPrefix = mockk<ModuleTypeMapper>()
    private val moduleTypeMapperBySuffix = mockk<ModuleTypeMapper>()

    private val moduleByType = mockk<RenderModule>()
    private val moduleByPrefix = mockk<RenderModule>()
    private val moduleBySuffix = mockk<RenderModule>()

    private lateinit var underTest: ModuleRegistry

    @BeforeEach
    fun setUp() {
        every { moduleTypeMapperByType.moduleTypeAssociations() } returns listOf(
            ModuleTypeDefinition(type = "file-any") to moduleByType
        )

        every { moduleTypeMapperByPrefix.moduleTypeAssociations() } returns listOf(
            ModuleTypeDefinition(mimeTypePrefix = "prefix-any") to moduleByPrefix
        )

        every { moduleTypeMapperBySuffix.moduleTypeAssociations() } returns listOf(
            ModuleTypeDefinition(type = "suffix-any") to moduleBySuffix
        )

        every { moduleByType.module() } returns "typeModule"
        every { moduleByPrefix.module() } returns "prefixModule"
        every { moduleBySuffix.module() } returns "suffixModule"

        underTest = ModuleRegistry(listOf(moduleTypeMapperByType, moduleTypeMapperByPrefix, moduleTypeMapperBySuffix))
    }

    @Test
    fun testGetModuleTypeMapperReturnsExpectedList() {
        // Act
        val result = underTest.getModuleTypeMapperList()

        // Assert
        assert(result == listOf(moduleTypeMapperByType, moduleTypeMapperByPrefix, moduleTypeMapperBySuffix))
    }

    @Test
    fun testModuleByNameReturnsExpected() {
        // Act and assert
        assertTrue(underTest.getRenderModule<RenderModule>("typeModule") == moduleByType)
        assertTrue(underTest.getRenderModule<RenderModule>("prefixModule") == moduleByPrefix)
        assertTrue(underTest.getRenderModule<RenderModule>("suffixModule") == moduleBySuffix)



    }

}