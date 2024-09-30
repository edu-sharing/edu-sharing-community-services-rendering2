package org.edu_sharing.rendering.core

import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RenderDataServiceTest {
    private val moduleRegistry = mockk<ModuleRegistry>()
    private val request = mockk<RenderDataRequest>()
    private val renderModule = mockk<RenderModule>()
    private lateinit var renderDataService: RenderDataService

    @BeforeEach
    fun setup() {
        renderDataService = RenderDataService(moduleRegistry = moduleRegistry)
    }

    @Test
    fun testGetRenderDataHandlesRequestAndReturnsResponse() {
        // Arrange
        val response = RenderDataResponse(jobId = "jobid")

        every { request.type } returns "someType"
        every { request.mimeType } returns "application/json"
        every { moduleRegistry.getRenderModule<RenderModule>("someType", "application/json") } returns renderModule
        every { renderModule.handle(request) } returns response

        // Act
        val result = renderDataService.getRenderData(request)

        // Assert
        verifySequence {
            request.type
            request.mimeType
            moduleRegistry.getRenderModule<RenderModule>("someType", "application/json")
            renderModule.handle(request)
        }

        assertEquals(response, result)
    }

    @Test
    fun testGetRenderDataThrowsExceptionIfRenderModuleMappingServiceThrowsException() {
        // Arrange
        every { request.type } returns "someType"
        every { request.mimeType } returns "application/json"
        every { moduleRegistry.getRenderModule<RenderModule>("someType", "application/json") } throws Exception()

        // Act and assert
        assertThrows<Exception> { renderDataService.getRenderData(request) }

        verifySequence {
            request.type
            request.mimeType
            moduleRegistry.getRenderModule<RenderModule>("someType", "application/json")
        }

    }
}