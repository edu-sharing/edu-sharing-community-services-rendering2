package org.edu_sharing.rendering.modules.av.video

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.video.VideoRenderModule
import org.edu_sharing.rendering.modules.video.VideoService
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
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
        every { videoService.isConversionObject(cacheObject) } returns false

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.module == "VIDEO")
        assert(result.jobId == null)
        assert(result.objectLinks == linkList)

        verifySequence {
            mapper.renderDataRequestToCacheObject(request)
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
            videoService.isConversionObject(cacheObject)
            videoService.getObjectLinks(cacheObject)
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
            videoService.isConversionObject(cacheObject)
            videoService.getObjectLinks(cacheObject)
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
}
