package org.edu_sharing.rendering.modules.av.video

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.queue.SubJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.core.AmqpTemplate

@ExtendWith(MockKExtension::class)
class VideoRenderModuleTest {
    private val nodeExpiration = 23L
    private val mapper = mockk<Mapper>()
    private val videoService = mockk<VideoService>()
    private val amqpTemplate = mockk<AmqpTemplate>()
    private val subJobRepository = mockk<SubJobRepository>()

    lateinit var underTest: VideoRenderModule

    @BeforeEach
    fun setup() {
        underTest = VideoRenderModule(
            nodePermissionExpirationTime = nodeExpiration,
            mapper = mapper,
            videoService = videoService,
            amqpTemplate = amqpTemplate,
            subJobRepository = subJobRepository
        )
    }

    @Test
    fun testModuleReturnsVideoModule() {
        assert(underTest.module() == "VIDEO")
    }

    @Test
    fun testHandleUsesDefaultStrategyForNonConversionObject() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val request = mockk<RenderDataRequest>()
        val linkList = listOf(
            ObjectLink(link = "link1"),
            ObjectLink(link = "link2")
        )

        every { mapper.renderDataRequestToCacheObject(request) } returns cacheObject
        every {videoService.getObjectLinks(cacheObject)} returns linkList
        every { videoService.isConversionObject(cacheObject) } returns false

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.module == "VIDEO")
        assert(result.jobId == null)
        assert(result.objectLinks == linkList)

        verifySequence {
            mapper.renderDataRequestToCacheObject(request)
            videoService.getObjectLinks(cacheObject)
            videoService.isConversionObject(cacheObject)
        }
    }

    @Test
    fun testHandleReturnsLinksAndNoJobIdIfAllNeededQualitiesAreFound() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val request = mockk<RenderDataRequest>()
        val linkList = listOf(
            ObjectLink(link = "link1"),
            ObjectLink(link = "link2")
        )
        every { mapper.renderDataRequestToCacheObject(request) } returns cacheObject
        every { videoService.isConversionObject(cacheObject) } returns true
        every { videoService.getObjectLinks(cacheObject) } returns linkList
        every { videoService.getMissingQualities(linkList) } returns emptyList()

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.module == "VIDEO")
        assert(result.jobId == null)
        assert(result.objectLinks == linkList)

        verifySequence {
            mapper.renderDataRequestToCacheObject(request)
            videoService.getObjectLinks(cacheObject)
            videoService.isConversionObject(cacheObject)
            videoService.getMissingQualities(linkList)
        }
    }

    @Test
    fun testHandleCreatesJobsForMissingQualitiesAndReturnsLinksToExisting() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val request = mockk<RenderDataRequest>()
        val linkList = listOf(
            ObjectLink(link = "link1"),
            ObjectLink(link = "link2")
        )
        val missingQualities = listOf(800)

        every { mapper.renderDataRequestToCacheObject(request) } returns cacheObject
        every { videoService.isConversionObject(cacheObject) } returns true
        every { videoService.getObjectLinks(cacheObject) } returns linkList
        every { videoService.getMissingQualities(linkList) } returns missingQualities
        every {
            videoService.retrieveOrCreateJob(
                cacheObject,
                "VIDEO",
                missingQualities
            )
        } returns "jobid123"

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.module == "VIDEO")
        assert(result.jobId == "jobid123")
        assert(result.objectLinks == linkList)

        verifySequence {
            mapper.renderDataRequestToCacheObject(request)
            videoService.getObjectLinks(cacheObject)
            videoService.isConversionObject(cacheObject)
            videoService.getMissingQualities(linkList)
            videoService.retrieveOrCreateJob(
                cacheObject,
                "VIDEO",
                missingQualities
            )
        }
    }

    @Test
    fun testGetObjectLinkFromJobDataReturnsRetrievedLink() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val subJob = mockk<SubJob>()
        val renderingJob = mockk<RenderingJob>()
        val link = ObjectLink(link = "link1")
        val linkList = listOf(link)

        every { mapper.renderingJobToCacheObject(renderingJob) } returns cacheObject
        every { subJob.quality } returns 123
        every { videoService.getObjectLinks(cacheObject, 123) } returns linkList

        // Act
        val result = underTest.getObjectLinkFromJobData(subJob, renderingJob)

        // Assert
        assert(result == linkList[0])
    }

    @Test
    fun testGetObjectLinkFromJobDataReturnsNullIfNullReturnedFromService() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val subJob = mockk<SubJob>()
        val renderingJob = mockk<RenderingJob>()

        every { mapper.renderingJobToCacheObject(renderingJob) } returns cacheObject
        every { subJob.quality } returns 123
        every { videoService.getObjectLinks(cacheObject, 123) } returns null

        // Act
        val result = underTest.getObjectLinkFromJobData(subJob, renderingJob)

        // Assert
        assert(result == null)
    }

    @Test
    fun testGetNodePermissionExpirationTimeReturnsSetTime() {
        assert(underTest.getNodePermissionExpirationTime() == nodeExpiration)
    }

    @Test
    fun testModuleTypeAssociationsReturnsDefinitionWithMimetypePrefix() {
        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        assert(result.size == 1)
        assert(result[0].first.type == null)
        assert(result[0].first.mimeTypeSuffix == null)
        assert(result[0].first.mimeTypePrefix == "video")
        assert(result[0].second == underTest)
    }

    @Test
    fun testCreateJobCreatesAndEnqueuesProperJob() {
        // Arrange
        val provider = JobDataProvider()
        val jobId = "507f191e810c19729de860ea"
        val job = provider.prepareJobForConversionModuleTesting(jobId, "AUDIO")
        val subJobList = mutableListOf<SubJob>()
        val subJobMessageList = mutableListOf<SubJobMessage>()


        underTest.avRoutingKey = "avRoutingKey"
        underTest.topicExchangeName = "topicExchangeName"

        val renderingMessage = mockk<RenderingJobMessage>()

        every {renderingMessage.missingQualities} returns listOf(1,2)
        every {subJobRepository.save(capture(subJobList))} returns mockk<SubJob>()
        justRun {amqpTemplate.convertAndSend("topicExchangeName", "avRoutingKey", capture(subJobMessageList))}

        // Act
        underTest.createJob(job, renderingMessage)

        // Assert
        assert(subJobList.size == 2)
        assert(subJobList[0].quality == 1)
        assert(subJobList[1].quality == 2)
        assert(subJobList[0].parent == job)
        assert(subJobList[1].parent == job)
        assert(subJobList[0].routingKey == "avRoutingKey")
        assert(subJobList[1].routingKey == "avRoutingKey")

        assert(subJobMessageList.size == 2)
        assert(subJobMessageList[0].quality == 1)
        assert(subJobMessageList[1].quality == 2)
        assert(subJobMessageList[0].id == jobId)
        assert(subJobMessageList[1].id == jobId)
    }
}
