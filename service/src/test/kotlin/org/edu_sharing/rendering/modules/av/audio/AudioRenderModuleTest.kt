package org.edu_sharing.rendering.modules.av.audio

import io.mockk.*
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.PriorityPostProcessor
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.queue.SubJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpTemplate

class AudioRenderModuleTest {
    private val audioService = mockk<AudioService>()
    private val mapper = mockk<Mapper>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val amqpTemplate = mockk<AmqpTemplate>()

    private val underTest = AudioRenderModule(
        nodePermissionExpirationTime = 45L,
        mapper = mapper,
        audioService = audioService,
        subJobRepository = subJobRepository,
        amqpTemplate = amqpTemplate,
        )

    private val cacheObject = CacheObject(
        nodeId = "nodeid",
        type = "audio",
        hash = "hash",
        repoId = "repoId"
    )

    @Test
    fun testHandleReturnsLinksToCachedObjectsIfAlreadyCached() {
        // Arrange
        val node = mockk<Node>()
        val linkList = listOf(ObjectLink(link = "mylink"))

        every { mapper.nodeToCacheObject(node) } returns cacheObject
        every { audioService.getObjectLinks(cacheObject) } returns linkList

        // Act
        val result = underTest.handle(
            node = node
        )

        // Assert
        assert(result.module == "AUDIO")
        assert(result.jobId == null)
        assert(result.objectLinks == linkList)

        verifySequence {
            mapper.nodeToCacheObject(node)
            audioService.getObjectLinks(cacheObject)
        }
    }

    @Test
    fun testHandleReturnsCreatedJobIdIfCreated() {
        // Arrange
        val node = mockk<Node>()

        every { mapper.nodeToCacheObject(node) } returns cacheObject
        every { audioService.getObjectLinks(cacheObject) } returns null
        every { audioService.retrieveOrCreateJob(cacheObject, "AUDIO") } returns "newJob123"

        // Act
        val result = underTest.handle(
            node = node
        )

        // Assert
        assert(result.module == "AUDIO")
        assert(result.jobId == "newJob123")
        assert(result.objectLinks == null)

        verifySequence {
            mapper.nodeToCacheObject(node)
            audioService.getObjectLinks(cacheObject)
            audioService.retrieveOrCreateJob(cacheObject, "AUDIO")
        }
    }

    @Test
    fun testGetObjectLinkFromJobDataReturnsObjectLinkReturnedFromService() {
        // Arrange
        val linkList = listOf(ObjectLink(link = "mylink"))
        val mockRenderingJob = mockk<RenderingJob>()
        val subJobMock = mockk<SubJob>()

        every { mapper.renderingJobToCacheObject(mockRenderingJob) } returns cacheObject
        every { audioService.getObjectLinks(cacheObject) } returns linkList

        // Act
        val result = underTest.getObjectLinkFromJobData(subJobMock, mockRenderingJob)

        // Assert
        assert(result?.link == "mylink")

        verifySequence {
            mapper.renderingJobToCacheObject(mockRenderingJob)
            audioService.getObjectLinks(cacheObject)
        }
    }

    @Test
    fun testGetObjectLinkFromJobDataReturnsNullReturnedFromService() {
        // Arrange
        val mockRenderingJob = mockk<RenderingJob>()
        val subJobMock = mockk<SubJob>()

        every { mapper.renderingJobToCacheObject(mockRenderingJob) } returns cacheObject
        every { audioService.getObjectLinks(cacheObject) } returns null

        // Act
        val result = underTest.getObjectLinkFromJobData(subJobMock, mockRenderingJob)

        // Assert
        assert(result == null)

        verifySequence {
            mapper.renderingJobToCacheObject(mockRenderingJob)
            audioService.getObjectLinks(cacheObject)
        }
    }


    @Test
    fun testGetNodePermissionExpirationTimeReturnsProperTime() {
        assert(underTest.getNodePermissionExpirationTime() == 45L)
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
        val postProcessorSlot = slot<PriorityPostProcessor>()

        every {renderingMessage.missingQualities} returns listOf(1,2)
        every {subJobRepository.save(capture(subJobList))} returns mockk<SubJob>()
        justRun {
            amqpTemplate.convertAndSend(
                "topicExchangeName",
                "avRoutingKey",
                capture(subJobMessageList),
                capture(postProcessorSlot)
            )
        }

        // Act
        underTest.createConversionSubJobs(job, renderingMessage)

        // Assert
        assert(postProcessorSlot.isCaptured)
        assert(postProcessorSlot.captured.priority == 255)


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
