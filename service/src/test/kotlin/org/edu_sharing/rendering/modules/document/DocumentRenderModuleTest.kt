package org.edu_sharing.rendering.modules.document

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verifySequence
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.queue.SubJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.http.MediaType

class DocumentRenderModuleTest {

    private val mockDocumentService = mockk<DocumentService>()
    private val mockMapper = mockk<Mapper>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val amqpTemplate = mockk<AmqpTemplate>()

    private val underTest = DocumentRenderModule(
        nodePermissionExpirationTime = 45L,
        mapper = mockMapper,
        documentService = mockDocumentService,
        subJobRepository = subJobRepository,
        amqpTemplate = amqpTemplate
    )

    private val cacheObject = CacheObject(
        nodeId = "nodeid",
        type = "doc",
        hash = "hash",
        repoId = "repo123"
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
        assert(result.module == "DOCUMENT")
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
        assert(result.module == "DOCUMENT")
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

    @Test
    fun testGetNodePermissionExpirationTimeReturnsProperTime() {
        assert(underTest.getNodePermissionExpirationTime() == 45L)
    }

    @Test
    fun testCreateJobCreatesAndEnqueuesProperJob() {
        // Arrange
        underTest.topicExchangeName = "myExchange"
        underTest.documentRoutingKey = "myRoutingKey"
        val subJobSlot = slot<SubJob>()
        val messageSlot = slot<SubJobMessage>()
        val renderingJob = mockk<RenderingJob>()
        val message = mockk<RenderingJobMessage>()
        val jobId = ObjectId()

        every { subJobRepository.save(capture(subJobSlot)) } returns mockk<SubJob>()
        every { renderingJob.subJobs } returns ArrayList()
        every { renderingJob.id } returns jobId
        justRun { amqpTemplate.convertAndSend("myExchange", "myRoutingKey", capture(messageSlot)) }

        // Act
        underTest.createConversionSubJobs(renderingJob, message)

        // Assert
        assert(subJobSlot.isCaptured)
        assert(subJobSlot.captured.routingKey == "myRoutingKey")
        assert(subJobSlot.captured.parent == renderingJob)
        assert(messageSlot.isCaptured)
        assert(messageSlot.captured.id == jobId.toString())
    }
}
       