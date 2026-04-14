package org.edu_sharing.rendering.modules.av.video

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.bson.types.ObjectId
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RequestUserData
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
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
    private val config = mockk<VideoConverterConfig>()

    lateinit var underTest: VideoRenderModule

    @BeforeEach
    fun setup() {
        underTest = VideoRenderModule(
            nodePermissionExpirationTime = nodeExpiration,
            mapper = mapper,
            videoService = videoService,
            amqpTemplate = amqpTemplate,
            subJobRepository = subJobRepository,
            configuredResolutions = config,
            avRoutingKey = "av_routing_key",
            topicExchangeName = "topic_exchange_name"
        )

    }

    @Test
    fun testModuleReturnsVideoModule() {
        assert(underTest.module() == "VIDEO")
    }

    @Test
    fun testHandleNonConversionTypeWithCache() {
        val node = mockk<Node>()
        val userData = mockk<RequestUserData>()
        val cacheObject = mockk<CacheObject>()
        val objectLinks = listOf(mockk<ObjectLink>())

        every { mapper.nodeToCacheObject(node) } returns cacheObject
        every { node.properties } returns mapOf("ccm:height" to listOf(""))
        every { videoService.isConversionObject(cacheObject) } returns false
        every { videoService.getObjectLinks(cacheObject, null, null) } returns objectLinks

        val result = underTest.handle(node)

        verify { videoService.isConversionObject(cacheObject) }
        verify { videoService.getObjectLinks(cacheObject, null, null) }
        assert(result.objectLinks == objectLinks)
        assert(result.module == "VIDEO")
        assert(result.jobId == null)
    }

    @Test
    fun testHandleConversionTypeWithAllQualities() {
        val node = mockk<Node>()
        val userData = mockk<RequestUserData>()
        val cacheObject = mockk<CacheObject>()
        val objectLinks = listOf(mockk<ObjectLink>())
        val height = 1080

        every { mapper.nodeToCacheObject(node) } returns cacheObject
        every { node.properties } returns mapOf("ccm:height" to listOf(height.toString()))
        every { videoService.isConversionObject(cacheObject) } returns true
        every { videoService.getObjectLinks(cacheObject, null, height) } returns objectLinks
        every { videoService.getMissingQualities(objectLinks, height) } returns emptyList()

        val result = underTest.handle(node)

        verify { videoService.isConversionObject(cacheObject) }
        verify { videoService.getObjectLinks(cacheObject, null, height) }
        verify { videoService.getMissingQualities(objectLinks, height) }
        assert(result.objectLinks == objectLinks)
        assert(result.module == "VIDEO")
        assert(result.jobId == null)
    }

    @Test
    fun testHandleConversionTypeWithMissingQualities() {
        val node = mockk<Node>()
        val userData = mockk<RequestUserData>()
        val cacheObject = mockk<CacheObject>()
        val objectLinks = listOf(mockk<ObjectLink>())
        val height = 1080
        val missingQualities = listOf(720)
        val jobId = "test-job-id"

        every { mapper.nodeToCacheObject(node) } returns cacheObject
        every { node.properties } returns mapOf("ccm:height" to listOf(height.toString()))
        every { videoService.isConversionObject(cacheObject) } returns true
        every { videoService.getObjectLinks(cacheObject, null, height) } returns objectLinks
        every { videoService.getMissingQualities(objectLinks, height) } returns missingQualities
        every { videoService.retrieveOrCreateJob(cacheObject, "VIDEO", missingQualities) } returns jobId

        val result = underTest.handle(node)

        verify { videoService.isConversionObject(cacheObject) }
        verify { videoService.getObjectLinks(cacheObject, null, height) }
        verify { videoService.getMissingQualities(objectLinks, height) }
        verify { videoService.retrieveOrCreateJob(cacheObject, "VIDEO", missingQualities) }
        assert(result.objectLinks == objectLinks)
        assert(result.module == "VIDEO")
        assert(result.jobId == jobId)
    }

    @Test
    fun testGetObjectLinkFromJobDataSuccess() {
        val subJob = mockk<SubJob>()
        val renderingJob = mockk<RenderingJob>()
        val cacheObject = mockk<CacheObject>()
        val objectLink = mockk<ObjectLink>()

        every { mapper.renderingJobToCacheObject(renderingJob) } returns cacheObject
        every { subJob.quality } returns 720
        every { videoService.getObjectLinks(cacheObject, 720) } returns listOf(objectLink)

        val result = underTest.getObjectLinkFromJobData(subJob, renderingJob)

        verify { mapper.renderingJobToCacheObject(renderingJob) }
        verify { videoService.getObjectLinks(cacheObject, 720) }
        assert(result == objectLink)
    }

    @Test
    fun testGetObjectLinkFromJobDataReturnsNull() {
        val subJob = mockk<SubJob>()
        val renderingJob = mockk<RenderingJob>()
        val cacheObject = mockk<CacheObject>()

        every { mapper.renderingJobToCacheObject(renderingJob) } returns cacheObject
        every { subJob.quality } returns 720
        every { videoService.getObjectLinks(cacheObject, 720) } returns null

        val result = underTest.getObjectLinkFromJobData(subJob, renderingJob)

        verify { mapper.renderingJobToCacheObject(renderingJob) }
        verify { videoService.getObjectLinks(cacheObject, 720) }
        assert(result == null)
    }

    @Test
    fun testGetNodePermissionExpirationTime() {
        assert(underTest.getNodePermissionExpirationTime() == nodeExpiration)
    }

    @Test
    fun testCreateConversionSubJobsWithMultipleResolutions() {
        val renderingJob = mockk<RenderingJob>()
        val message = mockk<RenderingJobMessage>()
        val subJobs = mutableListOf<SubJob>()
        val jobId = ObjectId()

        every { renderingJob.id } returns jobId
        every { renderingJob.subJobs } returns subJobs
        every { message.missingQualities } returns listOf(720, 1080)
        every { config.getPriority(720, 0) } returns 1
        every { config.getPriority(1080, 0) } returns 2
        every { subJobRepository.save(any()) } returns mockk()
        every { amqpTemplate.convertAndSend(any(), any(), any(), any()) } returns Unit

        underTest.createConversionSubJobs(renderingJob, message)

        verify(exactly = 2) { subJobRepository.save(any()) }
        verify(exactly = 2) { amqpTemplate.convertAndSend(any(), any(), any(), any()) }
        assert(subJobs.size == 2)
    }

    @Test
    fun testCreateConversionSubJobsWithEmptyResolutions() {
        val renderingJob = mockk<RenderingJob>()
        val message = mockk<RenderingJobMessage>()
        val subJobs = mutableListOf<SubJob>()

        every { renderingJob.subJobs } returns subJobs
        every { message.missingQualities } returns emptyList()

        underTest.createConversionSubJobs(renderingJob, message)

        verify(exactly = 0) { subJobRepository.save(any()) }
        verify(exactly = 0) { amqpTemplate.convertAndSend(any(), any(), any(), any()) }
        assert(subJobs.isEmpty())
    }

}
