package org.edu_sharing.rendering.modules.image

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.core.AmqpTemplate

@ExtendWith(MockKExtension::class)
class ImageRenderModuleTest {
    private val nodePermissionTime = 67L
    private val mapperMock = mockk<Mapper>()
    private val imageServiceMock = mockk<ImageService>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val amqpTemplate = mockk<AmqpTemplate>()

    lateinit var underTest: ImageRenderModule

    @BeforeEach
    fun setup() {
        underTest = ImageRenderModule(
            nodePermissionExpirationTime = nodePermissionTime,
            mapper = mapperMock,
            imageService = imageServiceMock,
            subJobRepository = subJobRepository,
            amqpTemplate = amqpTemplate
        )
        clearAllMocks()
    }

    @Test
    fun testHandleInvokesDefaultStrategyWithNonConversionObjects() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        val cacheObject = mockk<CacheObject>()

        val linkList = listOf(ObjectLink(link = "link1"), ObjectLink(link = "link2"))

        every { mapperMock.renderDataRequestToCacheObject(request) } returns cacheObject
        every { imageServiceMock.getObjectLinks(cacheObject = cacheObject) } returns linkList
        every { imageServiceMock.isConversionObject(cacheObject) } returns false

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.module == "IMAGE")
        assert(result.jobId == null)
        assert(result.objectLinks != null)
        assert(result.objectLinks!!.size == 2)
        assert(result.objectLinks!! == linkList)

        verifySequence {
            mapperMock.renderDataRequestToCacheObject(request)
            imageServiceMock.getObjectLinks(cacheObject = cacheObject)
            imageServiceMock.isConversionObject(cacheObject)
        }
    }

    @Test
    fun testHandleReturnsCachedQualitiesAndCreatesJobForMissing() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        val cacheObject = mockk<CacheObject>()

        val availableLinks = listOf(ObjectLink(link = "link1"))
        val missingQualities = listOf(100, 200)

        every { mapperMock.renderDataRequestToCacheObject(request) } returns cacheObject
        every { imageServiceMock.isConversionObject(cacheObject) } returns true
        every { imageServiceMock.getObjectLinks(cacheObject) } returns availableLinks
        every { imageServiceMock.getMissingQualities(availableLinks) } returns missingQualities
        every { imageServiceMock.retrieveOrCreateJob(cacheObject, "IMAGE", missingQualities) } returns "jobid1"

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.module == "IMAGE")
        assert(result.jobId == "jobid1")
        assert(result.objectLinks == availableLinks)

        verifySequence {
            mapperMock.renderDataRequestToCacheObject(request)
            imageServiceMock.getObjectLinks(cacheObject)
            imageServiceMock.isConversionObject(cacheObject)
            imageServiceMock.getMissingQualities(availableLinks)
            imageServiceMock.retrieveOrCreateJob(cacheObject, "IMAGE", missingQualities)
        }
    }

    @Test
    fun testHandleReturnsAllLinksIfNoMissingQualitiesFoundForConversionObject() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        val cacheObject = mockk<CacheObject>()

        val availableLinks = listOf(ObjectLink(link = "link1"))

        every { mapperMock.renderDataRequestToCacheObject(request) } returns cacheObject
        every { imageServiceMock.isConversionObject(cacheObject) } returns true
        every { imageServiceMock.getObjectLinks(cacheObject) } returns availableLinks
        every { imageServiceMock.getMissingQualities(availableLinks) } returns emptyList()

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.module == "IMAGE")
        assert(result.jobId == null)
        assert(result.objectLinks == availableLinks)

        verifySequence {
            mapperMock.renderDataRequestToCacheObject(request)
            imageServiceMock.getObjectLinks(cacheObject)
            imageServiceMock.isConversionObject(cacheObject)
            imageServiceMock.getMissingQualities(availableLinks)
        }
    }

    @Test
    fun testGetObjectLinksFromJobDataReturnsProperLink() {
        // Arrange
        val subJob = mockk<SubJob>()
        val job = mockk<RenderingJob>()
        val cacheObject = mockk<CacheObject>()

        every { mapperMock.renderingJobToCacheObject(job) } returns cacheObject
        every { subJob.quality } returns 100
        every { imageServiceMock.getObjectLinks(cacheObject, 100) } returns listOf(ObjectLink(link = "link1"))

        // Act
        val result = underTest.getObjectLinkFromJobData(subJob, job)

        // Assert
        assert(result?.link == "link1")

        verifySequence {
            mapperMock.renderingJobToCacheObject(job)
            subJob.quality
            imageServiceMock.getObjectLinks(cacheObject, 100)
        }
    }

    @Test
    fun testGetObjectLinksFromJobDataReturnsNullWhenNoLinkRetrieved() {
        // Arrange
        val subJob = mockk<SubJob>()
        val job = mockk<RenderingJob>()
        val cacheObject = mockk<CacheObject>()

        every { mapperMock.renderingJobToCacheObject(job) } returns cacheObject
        every { subJob.quality } returns 100
        every { imageServiceMock.getObjectLinks(cacheObject, 100) } returns null

        // Act
        val result = underTest.getObjectLinkFromJobData(subJob, job)

        // Assert
        assert(result == null)

        verifySequence {
            mapperMock.renderingJobToCacheObject(job)
            subJob.quality
            imageServiceMock.getObjectLinks(cacheObject, 100)
        }
    }

    @Test
    fun testModuleReturnsImageModule() {
        assert(underTest.module() == "IMAGE")
    }

    @Test
    fun testGetNodePermissionTimeReturnsSetTime() {
        assert(underTest.getNodePermissionExpirationTime() == nodePermissionTime)
    }
}
