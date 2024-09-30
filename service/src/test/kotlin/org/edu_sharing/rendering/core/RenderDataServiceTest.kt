package org.edu_sharing.rendering.core

class RenderDataServiceTest {
/**
    private val renderModuleMappingService = mockk<RenderModuleMappingService>()
    private val moduleRegistry = mockk<ModuleRegistry>()
    private val renderModule = mockk<RenderModule>()
    private val request = mockk<RenderDataRequest>()
    private lateinit var renderDataService: RenderDataService

    @BeforeEach
    fun setup() {
        renderDataService = RenderDataService(renderModuleMappingService, moduleRegistry)
    }

    @Test
    fun testGetRenderDataHandlesRequestAndReturnsResponse() {
        // Arrange
        val response = RenderDataResponse(jobId = "jobid")

        every { request.type } returns "someType"
        every { request.mimeType } returns "application/json"
        every { renderModuleMappingService.getModule("someType", "application/json") } returns RenderModules.PDF
        every { moduleRegistry.getRenderModule<RenderModule>(RenderModules.PDF) } returns renderModule
        every { renderModule.handle(request) } returns response

        // Act
        val result = renderDataService.getRenderData(request)

        // Assert
        verifySequence {
            request.type
            request.mimeType
            renderModuleMappingService.getModule(any(), any())
            moduleRegistry.getRenderModule<RenderModule>(any())
            renderModule.handle(request)
        }

        assertEquals(response, result)
    }

    @Test
    fun testGetRenderDataThrowsExceptionIfRenderModuleMappingServiceThrowsException() {
        // Arrange
        every { request.type } returns "someType"
        every { request.mimeType } returns "application/json"
        every { renderModuleMappingService.getModule("someType", "application/json") } throws Exception()

        // Act and assert
        assertThrows<Exception> { renderDataService.getRenderData(request) }

        verifySequence {
            request.type
            request.mimeType
            renderModuleMappingService.getModule(any(), any())
        }

    }
    */
}