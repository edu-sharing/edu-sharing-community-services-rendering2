package org.edu_sharing.rendering.modules.jupyter

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class JupyterReceiverTest {
    private val mainJobLogic = mockk<MainJobLogic>()
    private val mapper = mockk<Mapper>()
    private val jupyterConversionService = mockk<JupyterConversionService>()

    lateinit var underTest: JupyterReceiver

    @BeforeEach
    fun setup() {
        underTest = JupyterReceiver(
            mainJobLogic = mainJobLogic,
            mapper = mapper,
            jupyterConversionService = jupyterConversionService
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
        every { mapper.renderingJobToCacheObject(job) } returns cacheObject
        justRun { jupyterConversionService.process(cacheObject, job) }
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
            jupyterConversionService.process(cacheObject, job)
            mainJobLogic.processMainJob("job123")
        }

    }
}