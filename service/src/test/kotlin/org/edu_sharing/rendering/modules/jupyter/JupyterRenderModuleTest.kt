package org.edu_sharing.rendering.modules.jupyter

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.http.MediaType

@ExtendWith(MockKExtension::class)
class JupyterRenderModuleTest {
    private val nodePermissionExpirationTime = 55L
    private val mapper = mockk<Mapper>()
    private val jupyterJobService = mockk<JupyterJobService>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val amqpTemplate = mockk<AmqpTemplate>()

    lateinit var underTest: JupyterRenderModule

    @BeforeEach
    fun setup() {
        underTest = JupyterRenderModule(
            nodePermissionExpirationTime = nodePermissionExpirationTime,
            mapper = mapper,
            jupyterJobService = jupyterJobService,
            subJobRepository = subJobRepository,
            amqpTemplate = amqpTemplate
        )
        underTest.jupyterKey = "jupKey"
        underTest.topicExchangeName = "topicExchangeName"
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun testModuleReturnsJupyter() {
        assert(underTest.module() == "JUPYTER")
    }

    @Test
    fun testHandleReturnsExistingLinkIfCached() {

        // Arrange
        val request = mockk<RenderDataRequest>()
        val cacheObject = mockk<CacheObject>()
        val links = listOf(ObjectLink(link = "mylink"))

        every { mapper.renderDataRequestToCacheObject(request) } returns cacheObject
        every { jupyterJobService.getObjectLinks(cacheObject, underTest) } returns links

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.objectLinks?.size == 1)
        assert(result.objectLinks?.get(0)?.link == "mylink")
        assert(result.module == "JUPYTER")
        assert(result.jobId == null)

        verifySequence {
            mapper.renderDataRequestToCacheObject(request)
            jupyterJobService.getObjectLinks(cacheObject, underTest)
        }
    }

    @Test
    fun testHandleCreatesJobIfNoCachedDataFound() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        val cacheObject = mockk<CacheObject>()

        every { mapper.renderDataRequestToCacheObject(request) } returns cacheObject
        every { jupyterJobService.getObjectLinks(cacheObject, underTest) } returns null
        every { jupyterJobService.retrieveOrCreateJob(cacheObject, underTest) } returns "job123"

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.objectLinks == null)
        assert(result.jobId == "job123")
        assert(result.module == "JUPYTER")

        verifySequence {
            mapper.renderDataRequestToCacheObject(request)
            jupyterJobService.getObjectLinks(cacheObject, underTest)
            jupyterJobService.retrieveOrCreateJob(cacheObject, underTest)
        }
    }

    @Test
    fun testGetNodePermissionExpirationTime() {
        assert(underTest.getNodePermissionExpirationTime() == 55L)
    }

    @Test
    fun testModuleTypeAssociationsReturnsDefinitionWithMimetypePrefix() {
        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        assert(result.size == 1)
        assert(result[0].first.type == "file-jupyter")
        assert(result[0].first.mimeTypeSuffix == null)
        assert(result[0].first.mimeTypePrefix == null)
    }

    @Test
    fun testCreateJobCreatesAndEnqueuesJob() {
        // Arrange
        val renderingJob = mockk<RenderingJob>()
        val message = mockk<RenderingJobMessage>()
        val jobId = ObjectId()

        val savedSubJobSlot = slot<SubJob>()
        val addedSubJobSlot = slot<SubJob>()
        val subJobMessageSlot = slot<SubJobMessage>()

        every { subJobRepository.save(capture(savedSubJobSlot)) } returns mockk<SubJob>()
        every { renderingJob.subJobs.add(capture(addedSubJobSlot)) } returns true
        every { renderingJob.id } returns jobId
        justRun { amqpTemplate.convertAndSend("topicExchangeName", "jupKey", capture(subJobMessageSlot)) }

        // Act
        underTest.createJob(renderingJob, message)

        // Assert
        assert(savedSubJobSlot.isCaptured)
        assert(savedSubJobSlot.captured.routingKey == "jupKey")
        assert(savedSubJobSlot.captured.parent == renderingJob)
        assert(addedSubJobSlot.isCaptured)
        assert(savedSubJobSlot.captured.id == addedSubJobSlot.captured.id)
        assert(subJobMessageSlot.isCaptured)
        assert(subJobMessageSlot.captured.id == jobId.toString())
    }

    @Test
    fun testGetTargetMimetypeReturnsHtml() {
        assert(underTest.getTargetMimetype() == MediaType.TEXT_HTML_VALUE)
    }
}