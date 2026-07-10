package org.edu_sharing.rendering.modules.image

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.ErrorStrings
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.SubJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.image.BufferedImage

@ExtendWith(MockKExtension::class)
class ImageReceiverTest {
    private val mainJobLogic: MainJobLogic = mockk()
    private val subJobRepository: SubJobRepository = mockk()
    private val conversionService: ImageConversionService = mockk()
    private val mapper = Mapper()

    private lateinit var underTest: ImageReceiver

    @BeforeEach
    fun setup() {
        underTest = ImageReceiver(
            mainJobLogic = mainJobLogic,
            subJobRepository = subJobRepository,
            conversionService = conversionService,
            mapper = mapper
        )
    }

    @Test
    fun testReceiveMessageReturnsEarlyIfNoMainJobFound() {
        // Arrange
        val id = "507f191e810c19729de860ea"
        val subJobMessage = SubJobMessage(id = id)
        every { mainJobLogic.getMainJobEntry(id) } returns null
        // Act
        underTest.receiveMessage(subJobMessage)
        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(id) }
        confirmVerified(mainJobLogic)
    }

    @Test
    fun testReceiveMessageSetsSubJobStatusAccordingToConversionSuccess() {
        // Arrange
        val id = "507f191e810c19729de860ea"
        val subId1 = "507f191e810c19729de860eb"
        val subId2 = "507f191e810c19729de860ec"
        val job = prepareJobForTesting(id, subId1, subId2)
        val bufferedImage = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
        val subJobMessage = SubJobMessage(id = id)

        every { mainJobLogic.getMainJobEntry(id) } returns job
        every { conversionService.fetchSourceImage(any(), 200) } returns bufferedImage
        every { subJobRepository.save(any()) } returnsMany listOf(job.subJobs[0], job.subJobs[0], job.subJobs[1], job.subJobs[1])
        every { conversionService.convert(any(), job.subJobs[0].quality, bufferedImage) } throws Exception()
        justRun { conversionService.convert(any(), job.subJobs[1].quality, bufferedImage) }
        every { mainJobLogic.processMainJob(id) } returns true
        justRun { conversionService.deleteTempFile(any()) }
        // Act
        underTest.receiveMessage(subJobMessage)
        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(id) }
        verify(exactly = 1) { conversionService.fetchSourceImage(any(), 200) }
        verify(exactly = 4) {subJobRepository.save(any())}
        verify(exactly = 1) { conversionService.convert(any(), job.subJobs[0].quality, bufferedImage) }
        verify(exactly = 1) { conversionService.convert(any(), job.subJobs[1].quality, bufferedImage) }
        verify(exactly = 1) { mainJobLogic.processMainJob(id) }
        verify(exactly = 1) { conversionService.deleteTempFile(any()) }
        assert(job.subJobs[0].status == SubJobStatus.FAILED)
        assert(job.subJobs[1].status == SubJobStatus.FINISHED)
        confirmVerified(mainJobLogic, subJobRepository, conversionService)
    }

    @Test
    fun testReceiveMessageFailsAllSubJobsWhenFetchSourceImageThrows() {
        // Arrange
        val id = "507f191e810c19729de860ea"
        val subId1 = "507f191e810c19729de860eb"
        val subId2 = "507f191e810c19729de860ec"
        val job = prepareJobForTesting(id, subId1, subId2)
        val subJobMessage = SubJobMessage(id = id)

        every { mainJobLogic.getMainJobEntry(id) } returns job
        every { conversionService.fetchSourceImage(any(), 200) } throws ConversionException("Image too large")
        every { subJobRepository.save(any()) } returnsMany listOf(job.subJobs[0], job.subJobs[1])
        every { mainJobLogic.processMainJob(id) } returns true
        justRun { conversionService.deleteTempFile(any()) }
        // Act
        underTest.receiveMessage(subJobMessage)
        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(id) }
        verify(exactly = 1) { conversionService.fetchSourceImage(any(), 200) }
        verify(exactly = 2) { subJobRepository.save(any()) }
        verify(exactly = 0) { conversionService.convert(any(), any(), any()) }
        verify(exactly = 1) { conversionService.deleteTempFile(any()) }
        verify(exactly = 1) { mainJobLogic.processMainJob(id) }
        job.subJobs.forEach {
            assert(it.status == SubJobStatus.FAILED)
            assert(it.errorMessage == ErrorStrings.GENERIC_CONVERSION_ERROR)
        }
        confirmVerified(mainJobLogic, subJobRepository, conversionService)
    }

    fun prepareJobForTesting(id: String, subId1: String, subId2: String): RenderingJob {
        val job = RenderingJob(
            id = ObjectId(id),
            esHash = "hash",
            esObjectId = "esobjectid",
            esObjectType = "esobjecttype",
            mimeType = "image/jpeg",
            module = "IMAGE",
            nodeVersion = "1.2",
            repoId = "repoid",
            status = RenderingJobStatus.PROCESSING
        )
        val dummy = RenderingJob(
            esHash = "hash",
            esObjectId = "esobjectid",
            esObjectType = "esobjecttype",
            mimeType = "image/jpeg",
            module = "IMAGE",
            nodeVersion = "1.2",
            repoId = "repoid",
            status = RenderingJobStatus.PROCESSING
        )
        val subJob1 = SubJob(
            id = ObjectId(subId1),
            parent = dummy,
            routingKey = "image",
            status = SubJobStatus.QUEUED,
            quality = 100
        )
        val subJob2 = SubJob(
            id = ObjectId(subId2),
            parent = dummy,
            routingKey = "image",
            status = SubJobStatus.QUEUED,
            quality = 200
        )
        job.subJobs.add(subJob1)
        job.subJobs.add(subJob2)
        return job
    }
}