package org.edu_sharing.rendering.core

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.security.ModulePermissionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class RenderDataServiceTest {
    private val moduleRegistry = mockk<ModuleRegistry>()
    private val request = mockk<RenderDataRequest>()
    private val renderModule = mockk<RenderModule>()
    private val modulePermissionService = mockk<ModulePermissionService>()
    private lateinit var underTest: RenderDataService

    @BeforeEach
    fun setup() {
        underTest = RenderDataService(
            moduleRegistry = moduleRegistry,
            modulePermissionService = modulePermissionService,
            securityEnabled = true
        )
    }

    @Test
    fun testGetRenderDataHandlesRequestAndReturnsResponse() {
        // Arrange
        val response = RenderDataResponse(jobId = "jobid")

        every { request.type } returns "someType"
        every { request.mimeType } returns "application/json"
        every { request.replicationSource } returns ""
        every { moduleRegistry.getRenderModule<RenderModule>("someType", "application/json", "") } returns renderModule
        every { renderModule.handle(request) } returns response
        every { modulePermissionService.hasModuleAccess(renderModule) } returns true

        excludeRecords {
            request.type
            request.mimeType
            request.replicationSource
        }
        // Act
        val result = underTest.getRenderData(request)

        // Assert
        verifySequence {
            moduleRegistry.getRenderModule<RenderModule>("someType", "application/json", "")
            modulePermissionService.hasModuleAccess(renderModule)
            renderModule.handle(request)
        }

        assertEquals(response, result)
    }

    @Test
    fun testGetRenderDataThrowsExceptionIfNoModulePermissionIsGiven() {
        // Arrange

        every { request.type } returns "someType"
        every { request.mimeType } returns "application/json"
        every { request.replicationSource } returns ""
        every { moduleRegistry.getRenderModule<RenderModule>("someType", "application/json", "") } returns renderModule
        every { modulePermissionService.hasModuleAccess(renderModule) } returns false

        excludeRecords {
            request.type
            request.mimeType
            request.replicationSource
        }

        // Act
        assertThrows<Exception> { underTest.getRenderData(request) }

        // Assert
        verifySequence {
            moduleRegistry.getRenderModule<RenderModule>("someType", "application/json", "")
            modulePermissionService.hasModuleAccess(renderModule)
        }
    }

    @Test
    fun testGetRenderDataThrowsExceptionIfRenderModuleMappingServiceThrowsException() {
        // Arrange
        every { request.type } returns "someType"
        every { request.mimeType } returns "application/json"
        every { request.replicationSource } returns null
        every { moduleRegistry.getRenderModule<RenderModule>("someType", "application/json", "") } throws Exception()

        excludeRecords {
            request.type
            request.mimeType
            request.replicationSource
        }

        // Act and assert
        assertThrows<Exception> { underTest.getRenderData(request) }

        verify(exactly = 1) { moduleRegistry.getRenderModule<RenderModule>("someType", "application/json", "")
            moduleRegistry.getRenderModule<RenderModule>("someType", "application/json", "")
        }

    }
}