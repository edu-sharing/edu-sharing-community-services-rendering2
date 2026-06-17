package org.edu_sharing.rendering.modules.image

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
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
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ImageRenderModuleTest {
    private val nodePermissionTime = 67L
    private val mapperMock = mockk<Mapper>()
    private val imageServiceMock = mockk<ImageService>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val amqpTemplate = mockk<AmqpTemplate>()

    private val jobDataProvider = JobDataProvider()

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
    fun testHandleReturnsCachedLinksAndDoesNotCheckForMissingQualitiesWithNonConversionObjects() {
        // Arrange
        val cacheObject = mockk<CacheObject>(relaxed = true)
        val node = mockk<Node>()

        val linkList = listOf(ObjectLink(link = "link1"), ObjectLink(link = "link2"))

        every { mapperMock.nodeToCacheObject(node) } returns cacheObject
        every { imageServiceMock.getObjectLinks(cacheObject = cacheObject) } returns linkList
        every { imageServiceMock.isConversionObject(cacheObject) } returns false

        // Act
        val result = underTest.handle(
            node = node,
        )

        // Assert
        assert(result.module == "IMAGE")
        assert(result.jobId == null)
        assert(result.objectLinks != null)
        assert(result.objectLinks!!.size == 2)
        assert(result.objectLinks!! == linkList)

        verifySequence {
            mapperMock.nodeToCacheObject(node)
            imageServiceMock.getObjectLinks(cacheObject = cacheObject)
            imageServiceMock.isConversionObject(cacheObject)
        }
    }

    @Test
    fun testHandleCreatesCopyJobForMissingNonConversionObject() {
        // Arrange
        val node = mockk<Node>()
        val cacheObject = mockk<CacheObject>(relaxed = true)

        every { mapperMock.nodeToCacheObject(node) } returns cacheObject
        every { imageServiceMock.getObjectLinks(cacheObject = cacheObject) } returns null
        every { imageServiceMock.isConversionObject(cacheObject) } returns false
        every { imageServiceMock.retrieveOrCreateJob(cacheObject, "IMAGE", emptyList()) } returns "jobId123"

        // Act
        val result = underTest.handle(
            node = node
        )

        // Assert
        assert(result.module == "IMAGE")
        assert(result.jobId == "jobId123")
        assert(result.objectLinks == null)

        verifySequence {
            mapperMock.nodeToCacheObject(node)
            imageServiceMock.getObjectLinks(cacheObject = cacheObject)
            imageServiceMock.isConversionObject(cacheObject)
            imageServiceMock.retrieveOrCreateJob(cacheObject, "IMAGE", emptyList())
        }
    }

    @Test
    fun testHandleReturnsCachedQualitiesAndCreatesJobForMissing() {
        // Arrange
        val node = mockk<Node>()
        val cacheObject = mockk<CacheObject>(relaxed = true)

        val availableLinks = listOf(ObjectLink(link = "link1"))
        val missingQualities = listOf(100, 200)

        every { mapperMock.nodeToCacheObject(node) } returns cacheObject
        every { imageServiceMock.isConversionObject(cacheObject) } returns true
        every { imageServiceMock.getObjectLinks(cacheObject) } returns availableLinks
        every { imageServiceMock.getMissingQualities(availableLinks) } returns missingQualities
        every { imageServiceMock.retrieveOrCreateJob(cacheObject, "IMAGE", missingQualities) } returns "jobid1"

        // Act
        val result = underTest.handle(
            node = node,
        )

        // Assert
        assert(result.module == "IMAGE")
        assert(result.jobId == "jobid1")
        assert(result.objectLinks == availableLinks)

        verifySequence {
            mapperMock.nodeToCacheObject(node)
            imageServiceMock.getObjectLinks(cacheObject)
            imageServiceMock.isConversionObject(cacheObject)
            imageServiceMock.getMissingQualities(availableLinks)
            imageServiceMock.retrieveOrCreateJob(cacheObject, "IMAGE", missingQualities)
        }
    }

    @Test
    fun testHandleCreatesNewJobIfAllQualitiesAreMissing() {
        // Arrange
        val node = mockk<Node>()
        val cacheObject = mockk<CacheObject>(relaxed = true)
        val missingQualities = listOf(100, 200)

        every { mapperMock.nodeToCacheObject(node) } returns cacheObject
        every { imageServiceMock.getObjectLinks(cacheObject) } returns null
        every { imageServiceMock.isConversionObject(cacheObject) } returns true
        every { imageServiceMock.getMissingQualities(null) } returns missingQualities
        every { imageServiceMock.retrieveOrCreateJob(cacheObject, "IMAGE", missingQualities) } returns "jobid1"

        // Act
        val result = underTest.handle(
            node = node,
        )

        // Assert
        assert(result.module == "IMAGE")
        assert(result.jobId == "jobid1")
        assert(result.objectLinks == null)

        verifySequence {
            mapperMock.nodeToCacheObject(node)
            imageServiceMock.getObjectLinks(cacheObject)
            imageServiceMock.isConversionObject(cacheObject)
            imageServiceMock.getMissingQualities(null)
            imageServiceMock.retrieveOrCreateJob(cacheObject, "IMAGE", missingQualities)
        }
    }

    @Test
    fun testHandleReturnsAllLinksIfNoMissingQualitiesFoundForConversionObject() {
        // Arrange
        val node = mockk<Node>()
        val cacheObject = mockk<CacheObject>(relaxed = true)

        val availableLinks = listOf(ObjectLink(link = "link1"))

        every { mapperMock.nodeToCacheObject(node) } returns cacheObject
        every { imageServiceMock.isConversionObject(cacheObject) } returns true
        every { imageServiceMock.getObjectLinks(cacheObject) } returns availableLinks
        every { imageServiceMock.getMissingQualities(availableLinks) } returns emptyList()

        // Act
        val result = underTest.handle(
            node = node,
        )

        // Assert
        assert(result.module == "IMAGE")
        assert(result.jobId == null)
        assert(result.objectLinks == availableLinks)

        verifySequence {
            mapperMock.nodeToCacheObject(node)
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
        val cacheObject = mockk<CacheObject>(relaxed = true)

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
        val cacheObject = mockk<CacheObject>(relaxed = true)

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

    @Test
    fun testCreateJobCreatesOneSubJobPerMissingQuality() {
        // Arrange
        underTest.topicExchangeName = "topicExchangeName"
        underTest.imageRoutingKey = "imageRoutingKey"
        val job = jobDataProvider.getJobWithoutSubJobs("IMAGE")
        val message = RenderingJobMessage(
            id = "someJobId123",
            missingQualities = listOf(100, 200)
        )

        val subJobList = mutableListOf<SubJob>()

        every { subJobRepository.save(capture(subJobList)) } returns mockk<SubJob>()
        justRun {
            amqpTemplate.convertAndSend(
                "topicExchangeName",
                "imageRoutingKey",
                SubJobMessage(job.id.toString())
            )
        }

        // Act
        underTest.createConversionSubJobs(renderingJob = job, message = message)

        assertTrue(subJobList.size == 2, "Expected two subJobs to be created and inserted, got ${subJobList.size}.")
        assertTrue(
            subJobList[0].routingKey == "imageRoutingKey",
            "Expected first job's routing key to be imageRoutingKey, got ${subJobList[0].routingKey}"
        )
        assertTrue(subJobList[0].quality == 100, "Expected first job's quality to be 100, got ${subJobList[0].quality}")
        assertTrue(
            subJobList[1].routingKey == "imageRoutingKey",
            "Expected second job's routing key to be imageRoutingKey, got ${subJobList[1].routingKey}"
        )
        assertTrue(
            subJobList[1].quality == 200,
            "Expected second job's quality to be 200, got ${subJobList[1].quality}"
        )

        assertTrue(job.subJobs.size == 2, "Expected 2 sub jobs to be added to job, got ${job.subJobs.size}.")

        verifySequence {
            subJobRepository.save(any())
            subJobRepository.save(any())
            amqpTemplate.convertAndSend("topicExchangeName", "imageRoutingKey", SubJobMessage(job.id.toString()))
        }
    }
}
