package org.edu_sharing.rendering.modules

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.core.exception.ModuleNotRegisteredException
import org.edu_sharing.rendering.core.exception.ObjectTypeNotSupportedException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ModuleRegistryTest {
    private val moduleTypeMapperByType = mockk<ModuleTypeMapper>()
    private val moduleTypeMapperByPrefix = mockk<ModuleTypeMapper>()
    private val moduleTypeMapperByMimeType = mockk<ModuleTypeMapper>()
    private val moduleTypeMapperEmpty = mockk<ModuleTypeMapper>()

    private val moduleByType = mockk<RenderModule>()
    private val moduleByPrefix = mockk<RenderModule>()
    private val moduleByMimeType = mockk<RenderModule>()
    private val moduleEmpty = mockk<RenderModule>()

    lateinit var underTest: ModuleRegistry

    @BeforeEach
    fun setUp() {
        every { moduleTypeMapperByType.moduleTypeAssociations() } returns listOf(
            ModuleTypeDefinition(type = "file-any") to moduleByType
        )

        every { moduleTypeMapperByMimeType.moduleTypeAssociations() } returns listOf(
            ModuleTypeDefinition(mimeTypePrefix = "prefix-any", mimeTypeSuffix = "suffix-with-prefix") to moduleByMimeType
        )

        every { moduleTypeMapperByPrefix.moduleTypeAssociations() } returns listOf(
            ModuleTypeDefinition(mimeTypePrefix = "prefix-any") to moduleByPrefix
        )

        every { moduleTypeMapperEmpty.moduleTypeAssociations() } returns listOf(
            ModuleTypeDefinition() to moduleEmpty
        )

        every { moduleByType.module() } returns "typeModule"
        every { moduleByPrefix.module() } returns "prefixModule"
        every { moduleByMimeType.module() } returns "suffixModule"
        every { moduleEmpty.module() } returns "emptyModule"

        underTest = ModuleRegistry(listOf(
            moduleTypeMapperByType,
            moduleTypeMapperByPrefix,
            moduleTypeMapperByMimeType,
            moduleTypeMapperEmpty
        ))
    }

    @Test
    fun testGetModuleTypeMapperReturnsExpectedList() {
        // Act
        val result = underTest.getModuleTypeMapperList()

        // Assert
        assert(result == listOf(moduleTypeMapperByType, moduleTypeMapperByPrefix, moduleTypeMapperByMimeType, moduleTypeMapperEmpty))
    }

    @Test
    fun testModuleByNameReturnsExpected() {
        // Act and assert
        assertTrue(underTest.getRenderModule<RenderModule>("typeModule") == moduleByType)
        assertTrue(underTest.getRenderModule<RenderModule>("prefixModule") == moduleByPrefix)
        assertTrue(underTest.getRenderModule<RenderModule>("suffixModule") == moduleByMimeType)
        assertThrows<ModuleNotRegisteredException> { underTest.getRenderModule<RenderModule>("nonsenseModule") }
    }

    @Test
    fun testGetRenderModuleByMappingReturnsExpected() {
        // Act and assert
        assertTrue(underTest.getRenderModule<RenderModule>(type = "file-any", mimeType = "prefix-any/suffix-any")
                == moduleByType)
        assertTrue(underTest.getRenderModule<RenderModule>(type = "file-nonsense", mimeType = "prefix-any/suffix-with-prefix")
                == moduleByMimeType)
        assertTrue(underTest.getRenderModule<RenderModule>(type = "file-nonsense", mimeType = "prefix-any/nonsense")
                == moduleByPrefix)
        assertThrows<ObjectTypeNotSupportedException> {
            underTest.getRenderModule<RenderModule>(type = "file-nonsense", mimeType = "prefix-nonsense/nonsense")
        }
    }
}