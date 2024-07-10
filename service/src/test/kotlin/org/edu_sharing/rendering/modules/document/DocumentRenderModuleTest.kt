package org.edu_sharing.rendering.modules.document

import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class DocumentRenderModuleTest {

    private val mockDocumentService = mockk<DocumentService>()
    private val mockMapper = mockk<Mapper>()

    private val underTest = DocumentRenderModule(mockMapper, mockDocumentService)

    private val cacheObject = CacheObject(
        nodeId = "nodeid",
        type = "doc",
        hash = "hash"
    )

    @Test
    fun testHandleReturnsLinksToCachedObjectsIfAlreadyCached() {
        // Arrange
        val mockRenderDataRequest = mockk<RenderDataRequest>()
        val linkList = listOf(ObjectLink(link = "mylink"))

        every { mockMapper.renderDataRequestToCacheObject(mockRenderDataRequest) } returns cacheObject
        every { mockDocumentService.getObjectLinks(cacheObject, underTest) } returns linkList

        // Act
        val result = underTest.handle(mockRenderDataRequest)

        // Assert
        assert(result.module == RenderModules.DOCUMENT)
        assert(result.jobId == null)
        assert(result.objectLinks == linkList)

        verifySequence {
            mockMapper.renderDataRequestToCacheObject(mockRenderDataRequest)
            mockDocumentService.getObjectLinks(cacheObject, underTest)
        }
    }

    @Test
    fun testHandleReturnsCreatedJobIdIfCreated() {
        // Arrange
        val mockRenderDataRequest = mockk<RenderDataRequest>()

        every { mockMapper.renderDataRequestToCacheObject(mockRenderDataRequest) } returns cacheObject
        every { mockDocumentService.getObjectLinks(cacheObject, underTest) } returns null
        every { mockDocumentService.retrieveOrCreateJob(cacheObject, underTest) } returns "newJob123"

        // Act
        val result = underTest.handle(mockRenderDataRequest)

        // Assert
        assert(result.module == RenderModules.DOCUMENT)
        assert(result.jobId == "newJob123")
        assert(result.objectLinks == null)

        verifySequence {
            mockMapper.renderDataRequestToCacheObject(mockRenderDataRequest)
            mockDocumentService.getObjectLinks(cacheObject, underTest)
            mockDocumentService.retrieveOrCreateJob(cacheObject, underTest)
        }
    }

    @Test
    fun testGetObjectLinkFromJobDataReturnsObjectLinkReturnedFromService() {
        // Arrange
        val linkList = listOf(ObjectLink(link = "mylink"))
        val mockRenderingJob = mockk<RenderingJob>()
        val subJobMock = mockk<SubJob>()

        every { mockMapper.renderingJobToCacheObject(mockRenderingJob) } returns cacheObject
        every { mockDocumentService.getObjectLinks(cacheObject, underTest) } returns linkList

        // Act
        val result = underTest.getObjectLinkFromJobData(subJobMock, mockRenderingJob)

        // Assert
        assert(result?.link == "mylink")

        verifySequence {
            mockMapper.renderingJobToCacheObject(mockRenderingJob)
            mockDocumentService.getObjectLinks(cacheObject, underTest)
        }
    }

    @Test
    fun testGetObjectLinkFromJobDataReturnsNullReturnedFromService() {
        // Arrange
        val mockRenderingJob = mockk<RenderingJob>()
        val subJobMock = mockk<SubJob>()

        every { mockMapper.renderingJobToCacheObject(mockRenderingJob) } returns cacheObject
        every { mockDocumentService.getObjectLinks(cacheObject, underTest) } returns null

        // Act
        val result = underTest.getObjectLinkFromJobData(subJobMock, mockRenderingJob)

        // Assert
        assert(result == null)

        verifySequence {
            mockMapper.renderingJobToCacheObject(mockRenderingJob)
            mockDocumentService.getObjectLinks(cacheObject, underTest)
        }
    }

    @Test
    fun testGetTargetMimeTypeReturnsPdf() {
        // Act and assert
        assert(underTest.getTargetMimetype() == MediaType.APPLICATION_PDF_VALUE)
    }
}