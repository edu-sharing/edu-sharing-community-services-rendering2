package org.edu_sharing.rendering.processing.image

import io.mockk.*
import org.bson.types.ObjectId
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJobs.SubJobMessage
import org.edu_sharing.rendering.renderingJobs.entity.JobStatus
import org.edu_sharing.rendering.renderingJobs.entity.RenderingJob
import org.edu_sharing.rendering.renderingJobs.entity.SubJob
import org.edu_sharing.rendering.processing.MainJobLogic
import org.edu_sharing.rendering.renderingJobs.repository.SubJobRepository
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class ImageReceiverTest {
    private val mainJobLogic: MainJobLogic = mockk()
    private val subJobRepository: SubJobRepository = mockk()
    private val conversionService: ImageConversionService = mockk()
    private val mapper = Mapper()
    private val imageReceiver = ImageReceiver(mainJobLogic, subJobRepository, conversionService, mapper)

    @Test
    fun testReceiveMessageReturnsEarlyIfNoMainJobFound() {
        // Arrange
        val id = "507f191e810c19729de860ea"
        val subJobMessage = SubJobMessage(id = id)
        every { mainJobLogic.getMainJobEntry(id) } returns null
        // Act
        imageReceiver.receiveMessage(subJobMessage)
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
        every { conversionService.fetchSourceImage(any()) } returns bufferedImage
        every { subJobRepository.save(any()) } returns job.subJobs[0]
        every { conversionService.convert(any(), job.subJobs[0].quality, bufferedImage) } throws Exception()
        justRun { conversionService.convert(any(), job.subJobs[1].quality, bufferedImage) }
        every { mainJobLogic.processMainJob(id) } returns true
        // Act
        imageReceiver.receiveMessage(subJobMessage)
        // Assert
        verify(exactly = 1) { mainJobLogic.getMainJobEntry(id) }
        verify(exactly = 1) { conversionService.fetchSourceImage(any()) }
        verify(exactly = 4) {subJobRepository.save(any())}
        verify(exactly = 1) { conversionService.convert(any(), job.subJobs[0].quality, bufferedImage) }
        verify(exactly = 1) { conversionService.convert(any(), job.subJobs[1].quality, bufferedImage) }
        verify(exactly = 1) { mainJobLogic.processMainJob(id) }
        assert(job.subJobs[0].status == JobStatus.FAILED)
        assert(job.subJobs[1].status == JobStatus.FINISHED)
        confirmVerified(mainJobLogic, subJobRepository, conversionService)
    }

    fun prepareJobForTesting(id: String, subId1: String, subId2: String): RenderingJob {
        val job = RenderingJob(
            id = ObjectId(id),
            esHash = "hash",
            esObjectId = "esobjectid",
            esObjectType = "esobjecttype",
            mimeType = "image/jpeg",
            module = RenderModules.IMAGE,
            nodeVersion = "1.2",
            repoId = "repoid",
            status = JobStatus.PROCESSING
        )
        val dummy = RenderingJob(
            esHash = "hash",
            esObjectId = "esobjectid",
            esObjectType = "esobjecttype",
            mimeType = "image/jpeg",
            module = RenderModules.IMAGE,
            nodeVersion = "1.2",
            repoId = "repoid",
            status = JobStatus.PROCESSING
        )
        val subJob1 = SubJob(
            id = ObjectId(subId1),
            parent = dummy,
            routingKey = "image",
            status = JobStatus.QUEUED,
            quality = 100
        )
        val subJob2 = SubJob(
            id = ObjectId(subId2),
            parent = dummy,
            routingKey = "image",
            status = JobStatus.QUEUED,
            quality = 200
        )
        job.subJobs.add(subJob1)
        job.subJobs.add(subJob2)
        return job
    }
}