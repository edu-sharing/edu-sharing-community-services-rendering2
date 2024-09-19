package org.edu_sharing.rendering.processing

import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.bson.types.ObjectId
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.SubJobMessage
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.service.ContentTransferService
import org.junit.Test
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.data.repository.findByIdOrNull
import java.io.ByteArrayInputStream

class JobReceiverTest {
    private val jobRepository: RenderingJobRepository = mockk()
    private val subJobRepository: SubJobRepository = mockk()
    private val storageService: StorageService = mockk()
    private val amqpTemplate: AmqpTemplate = mockk()
    private val mapper = Mapper()
    private val contentTransferService: ContentTransferService = mockk()
    private val jobReceiver = JobReceiver(
        jobRepository = jobRepository,
        subJobRepository = subJobRepository,
        storageImplementation = storageService,
        amqpTemplate = amqpTemplate,
        mapper = mapper,
        contentTransferService = contentTransferService
    )

    @Test
    fun testReceiveMessageReturnsIfNoJobFound() {
        val jobId = "507f191e810c19729de860ea"
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns null
        val message = RenderingJobMessage(jobId)
        jobReceiver.receiveMessage(message)
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        confirmVerified(jobRepository)
    }

    @Test
    fun testReceiveMessageSetsJobToFailedAndReturnsIfPuttingTempFileFails() {
        val jobId = "507f191e810c19729de860ea"
        val job = prepareJobForTesting(jobId)
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val contentInputStream = ByteArrayInputStream("coolcontent".toByteArray())
        val message = RenderingJobMessage(jobId)
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns job
        every { contentTransferService.getAsInputStream(cacheObject)} returns contentInputStream
        every { storageService.putTempFile(cacheObject, contentInputStream)} throws Exception()
        every { jobRepository.save(any()) } returns job
        jobReceiver.receiveMessage(message)
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        verify(exactly = 1) { contentTransferService.getAsInputStream(cacheObject)}
        verify(exactly = 1) { storageService.putTempFile(cacheObject, contentInputStream)}
        verify(exactly = 2) { jobRepository.save(any()) }
        assertThat(job.status).isEqualTo(JobStatus.FAILED)
        confirmVerified()
    }

    @Test
    fun testReceiveMessageCreatesAndEnqueuesImageJobIfImageModuleDetected() {
        // Arrange
        val jobId = "507f191e810c19729de860ea"
        val job = prepareJobForTesting(jobId, RenderModules.IMAGE)
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val contentInputStream = ByteArrayInputStream("coolcontent".toByteArray())
        val message = RenderingJobMessage(id = jobId, missingQualities = listOf(1,2))
        val routingKey = "image"
        val exchangeName = "exchange"
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns job
        every { contentTransferService.getAsInputStream(cacheObject)} returns contentInputStream
        justRun { storageService.putTempFile(cacheObject, contentInputStream) }
        every { jobRepository.save(any()) } returns job
        every { subJobRepository.save(any()) } returns SubJob(routingKey = "image", quality = 1, parent = job)
        every { amqpTemplate.convertAndSend(exchangeName, routingKey, SubJobMessage(jobId)) } returns Unit
        jobReceiver.imageRoutingKey = routingKey
        jobReceiver.topicExchangeName = exchangeName
        // Act
        jobReceiver.receiveMessage(message)
        // Assert
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        verify(exactly = 1) { contentTransferService.getAsInputStream(cacheObject)}
        verify(exactly = 1) { storageService.putTempFile(cacheObject, contentInputStream)}
        verify(exactly = 2) { subJobRepository.save(any()) }
        verify(exactly = 1) { jobRepository.save(any()) }
        verify(exactly = 1) { amqpTemplate.convertAndSend(exchangeName, routingKey, SubJobMessage(jobId)) }
        assertThat(job.status).isEqualTo(JobStatus.PROCESSING)
        assertThat(job.subJobs.size == 2)
        assertThat(job.subJobs[0].quality == 1)
        assertThat(job.subJobs[1].quality == 2)
        confirmVerified()
    }

    @Test
    fun testReceiveMessageCreatesAndEnqueuesVideoJobsIfVideoModuleDetected() {
        // Arrange
        val jobId = "507f191e810c19729de860ea"
        val job = prepareJobForTesting(jobId, RenderModules.VIDEO)
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val contentInputStream = ByteArrayInputStream("coolcontent".toByteArray())
        val message = RenderingJobMessage(id = jobId, missingQualities = listOf(1,2))
        val routingKey = "video"
        val exchangeName = "exchange"
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns job
        every { contentTransferService.getAsInputStream(cacheObject)} returns contentInputStream
        justRun { storageService.putTempFile(cacheObject, contentInputStream) }
        every { jobRepository.save(any()) } returns job
        every { subJobRepository.save(any()) } returns SubJob(routingKey = "video", quality = 1, parent = job)
        every { amqpTemplate.convertAndSend(exchangeName, routingKey, SubJobMessage(jobId,1)) } returns Unit
        every { amqpTemplate.convertAndSend(exchangeName, routingKey, SubJobMessage(jobId,2)) } returns Unit
        jobReceiver.avRoutingKey = routingKey
        jobReceiver.topicExchangeName = exchangeName
        // Act
        jobReceiver.receiveMessage(message)
        // Assert
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        verify(exactly = 1) { contentTransferService.getAsInputStream(cacheObject)}
        verify(exactly = 1) { storageService.putTempFile(cacheObject, contentInputStream)}
        verify(exactly = 2) { subJobRepository.save(any()) }
        verify(exactly = 1) { jobRepository.save(any()) }
        verify(exactly = 1) { amqpTemplate.convertAndSend(exchangeName, routingKey, SubJobMessage(jobId,1)) }
        verify(exactly = 1) { amqpTemplate.convertAndSend(exchangeName, routingKey, SubJobMessage(jobId,2)) }
        assertThat(job.status).isEqualTo(JobStatus.PROCESSING)
        assertThat(job.subJobs.size == 2)
        assertThat(job.subJobs[0].quality == 1)
        assertThat(job.subJobs[1].quality == 2)
        confirmVerified()
    }

    @Test
    fun testReceiveMessageCreatesAndEnqueuesAudioJobIfAudioModuleDetected() {
        // Arrange
        val jobId = "507f191e810c19729de860ea"
        val job = prepareJobForTesting(jobId, RenderModules.AUDIO)
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val contentInputStream = ByteArrayInputStream("coolcontent".toByteArray())
        val message = RenderingJobMessage(id = jobId, missingQualities = listOf(1))
        val routingKey = "video"
        val exchangeName = "exchange"
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns job
        every { contentTransferService.getAsInputStream(cacheObject)} returns contentInputStream
        justRun { storageService.putTempFile(cacheObject, contentInputStream) }
        every { jobRepository.save(any()) } returns job
        every { subJobRepository.save(any()) } returns SubJob(routingKey = "video", quality = 1, parent = job)
        every { amqpTemplate.convertAndSend(exchangeName, routingKey, SubJobMessage(jobId,1)) } returns Unit
        jobReceiver.avRoutingKey = routingKey
        jobReceiver.topicExchangeName = exchangeName
        // Act
        jobReceiver.receiveMessage(message)
        // Assert
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        verify(exactly = 1) { contentTransferService.getAsInputStream(cacheObject)}
        verify(exactly = 1) { storageService.putTempFile(cacheObject, contentInputStream)}
        verify(exactly = 1) { subJobRepository.save(any()) }
        verify(exactly = 1) { jobRepository.save(any()) }
        verify(exactly = 1) { amqpTemplate.convertAndSend(exchangeName, routingKey, SubJobMessage(jobId,1)) }
        assertThat(job.status).isEqualTo(JobStatus.PROCESSING)
        assertThat(job.subJobs.size == 1)
        assertThat(job.subJobs[0].quality == 1)
        confirmVerified()
    }

    @Test
    fun testReceiveMessageCreatesAndEnqueuesDocumentJobIfDocumentModuleDetected() {
        // Arrange
        val jobId = "507f191e810c19729de860ea"
        val job = prepareJobForTesting(jobId, RenderModules.DOCUMENT)
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val contentInputStream = ByteArrayInputStream("coolcontent".toByteArray())
        val message = RenderingJobMessage(id = jobId)
        val routingKey = "document"
        val exchangeName = "exchange"
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns job
        every { contentTransferService.getAsInputStream(cacheObject)} returns contentInputStream
        justRun { storageService.putTempFile(cacheObject, contentInputStream) }
        every { jobRepository.save(any()) } returns job
        every { subJobRepository.save(any()) } returns SubJob(routingKey = "document", parent = job)
        every { amqpTemplate.convertAndSend(exchangeName, routingKey, SubJobMessage(jobId)) } returns Unit
        jobReceiver.documentRoutingKey = routingKey
        jobReceiver.topicExchangeName = exchangeName
        // Act
        jobReceiver.receiveMessage(message)
        // Assert
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        verify(exactly = 1) { contentTransferService.getAsInputStream(cacheObject)}
        verify(exactly = 1) { storageService.putTempFile(cacheObject, contentInputStream)}
        verify(exactly = 1) { subJobRepository.save(any()) }
        verify(exactly = 1) { jobRepository.save(any()) }
        verify(exactly = 1) { amqpTemplate.convertAndSend(exchangeName, routingKey, SubJobMessage(jobId)) }
        assertThat(job.status).isEqualTo(JobStatus.PROCESSING)
        assertThat(job.subJobs.size == 1)
        confirmVerified()
    }

    @Test
    fun testReceiveMessageCreatesAndEnqueuesDocumentJobIfSpreadsheetModuleDetected() {
        // Arrange
        val jobId = "507f191e810c19729de860ea"
        val job = prepareJobForTesting(jobId, RenderModules.SPREADSHEET)
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val contentInputStream = ByteArrayInputStream("coolcontent".toByteArray())
        val message = RenderingJobMessage(id = jobId)
        val routingKey = "document"
        val exchangeName = "exchange"
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns job
        every { contentTransferService.getAsInputStream(cacheObject)} returns contentInputStream
        justRun { storageService.putTempFile(cacheObject, contentInputStream) }
        every { jobRepository.save(any()) } returns job
        every { subJobRepository.save(any()) } returns SubJob(routingKey = "document", parent = job)
        every { amqpTemplate.convertAndSend(exchangeName, routingKey, SubJobMessage(jobId)) } returns Unit
        jobReceiver.documentRoutingKey = routingKey
        jobReceiver.topicExchangeName = exchangeName
        // Act
        jobReceiver.receiveMessage(message)
        // Assert
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        verify(exactly = 1) { contentTransferService.getAsInputStream(cacheObject)}
        verify(exactly = 1) { storageService.putTempFile(cacheObject, contentInputStream)}
        verify(exactly = 1) { subJobRepository.save(any()) }
        verify(exactly = 1) { jobRepository.save(any()) }
        verify(exactly = 1) { amqpTemplate.convertAndSend(exchangeName, routingKey, SubJobMessage(jobId)) }
        assertThat(job.status).isEqualTo(JobStatus.PROCESSING)
        assertThat(job.subJobs.size == 1)
        confirmVerified()
    }

    @Test
    fun testReceiveMessageSetsJobToFailedAndReturnsIfModuleCannotBeMapped() {
        val jobId = "507f191e810c19729de860ea"
        val job = prepareJobForTesting(jobId, RenderModules.PDF)
        val cacheObject = mapper.renderingJobToCacheObject(job)
        val contentInputStream = ByteArrayInputStream("coolcontent".toByteArray())
        val message = RenderingJobMessage(jobId)
        every { jobRepository.findByIdOrNull(ObjectId(jobId)) } returns job
        every { contentTransferService.getAsInputStream(cacheObject)} returns contentInputStream
        justRun { storageService.putTempFile(cacheObject, contentInputStream)}
        every { jobRepository.save(any()) } returns job
        jobReceiver.receiveMessage(message)
        verify(exactly = 1) { jobRepository.findByIdOrNull(ObjectId(jobId)) }
        verify(exactly = 1) { contentTransferService.getAsInputStream(cacheObject)}
        verify(exactly = 1) { storageService.putTempFile(cacheObject, contentInputStream)}
        verify(exactly = 2) { jobRepository.save(any()) }
        assertThat(job.status).isEqualTo(JobStatus.FAILED)
        confirmVerified()
    }

    private fun prepareJobForTesting(id: String, module: RenderModules = RenderModules.VIDEO): RenderingJob {
        val job = RenderingJob(
            id = ObjectId(id),
            esHash = "hash",
            esObjectId = "esobjectid",
            esObjectType = "esobjecttype",
            mimeType = "multipart/form-data",
            module = module,
            nodeVersion = "1.2",
            repoId = "repoid",
            status = JobStatus.QUEUED
        )
        return job
    }

}