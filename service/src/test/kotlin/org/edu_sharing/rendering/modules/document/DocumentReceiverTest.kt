package org.edu_sharing.rendering.modules.document

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class DocumentReceiverTest {
    private val mainJobLogic: MainJobLogic = mockk()
    private val mapper = mockk<Mapper>()
    private val documentConversionService: DocumentConversionService = mockk()
    private val renderingJobRepository: RenderingJobRepository = mockk()

    lateinit var underTest: DocumentReceiver

    @BeforeEach
    fun setup() {
        underTest = DocumentReceiver(
            mainJobLogic = mainJobLogic,
            mapper = mapper,
            documentConversionService = documentConversionService,
            renderingJobRepository = renderingJobRepository
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun testReceiveMessageReturnsWithoutModifyingActionsOnMissingMainJob() {
        // Arrange
        val message = mockk<RenderingJobMessage>()
        every { message.id } returns "job123"
        every { mainJobLogic.getMainJobEntry("job123") } returns null

        // Act and assert
        underTest.receiveMessage(message)
    }

    @Test
    fun testReceiveMessageReturnsWithoutModifyingActionsOnMissingSubJobs() {
        // Arrange
        val message = mockk<RenderingJobMessage>()
        val job = mockk<RenderingJob>()
        every { message.id } returns "job123"
        every { mainJobLogic.getMainJobEntry("job123") } returns job
        every { job.subJobs } returns mutableListOf()

        // Act and assert
        underTest.receiveMessage(message)
    }

    @Test
    fun testReceiveMessageProcessesJobCorrectly() {
        // Arrange
        val message = mockk<RenderingJobMessage>()
        val job = mockk<RenderingJob>()
        val cacheObject = mockk<CacheObject>()
        every { message.id } returns "job123"
        every { mainJobLogic.getMainJobEntry("job123") } returns job
        every { job.subJobs } returns mutableListOf(mockk<SubJob>())
        every { renderingJobRepository.save(job) } returns job
        justRun { job.status = RenderingJobStatus.PROCESSING }
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        justRun { documentConversionService.process(cacheObject, job) }
        every { mainJobLogic.processMainJob("job123") } returns true

        excludeRecords {
            message.id
            job.subJobs
        }

        // Act
        underTest.receiveMessage(message)

        // Assert
        verifySequence {
            mainJobLogic.getMainJobEntry("job123")
            mapper.renderingJobToCacheObject(job)
            documentConversionService.process(cacheObject, job)
            mainJobLogic.processMainJob("job123")
        }
    }
}
