package org.edu_sharing.rendering.modules.pdf

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.modules.DefaultStrategy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PdfRenderModuleTest {
    private val defaultStrategyMock = mockk<DefaultStrategy>()
    private val mapperMock = mockk<Mapper>()

    private lateinit var underTest: PdfRenderModule

    @BeforeEach
    fun setup() {
        underTest = PdfRenderModule(55L, defaultStrategyMock, mapperMock)
        clearAllMocks()
    }

    @Test
    fun testHandleReturnsLinksFromDefaultStrategy() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        val cacheObject = mockk<CacheObject>()
        val linkList = listOf(ObjectLink(link = "mylink"))

        every { mapperMock.renderDataRequestToCacheObject(request) } returns cacheObject
        every { defaultStrategyMock.getObjectLinkList(cacheObject) } returns linkList

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.objectLinks?.get(0)?.link == "mylink")
        assert(result.module == RenderModules.PDF)
        assert(result.jobId == null)

        verifySequence {
            mapperMock.renderDataRequestToCacheObject(request)
            defaultStrategyMock.getObjectLinkList(cacheObject)
        }
    }

    @Test
    fun testModuleReturnsHtmlRenderModule() {
        assert(underTest.module() == RenderModules.PDF)
    }

    @Test
    fun testGetObjectLinkFromJobDataReturnsNull() {
        val subJobMock = mockk<SubJob>()
        val renderingJobMock = mockk<RenderingJob>()
        assert(underTest.getObjectLinkFromJobData(subJobMock, renderingJobMock) == null)
    }

    @Test
    fun testGetNodePermissionExpirationTimeReturnsProperTime() {
        assert(underTest.getNodePermissionExpirationTime() == 55L)
    }
}