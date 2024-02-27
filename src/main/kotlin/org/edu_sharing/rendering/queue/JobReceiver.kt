package org.edu_sharing.rendering.queue

import org.bson.types.ObjectId
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.RenderingJobMessage
import org.edu_sharing.rendering.dto.queue.SubJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class JobReceiver(
    @Qualifier("webApplicationContext") private val resourceLoader: ResourceLoader,
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val storageImplementation: StorageService,
    private val amqpTemplate: AmqpTemplate,
    private val mapper: Mapper
) {
    @Value("\${edu_sharing.imageRoutingKey}")
    lateinit var imageRoutingKey: String

    @Value("\${edu_sharing.topicExchangeName}")
    lateinit var topicExchangeName: String
    fun receiveMessage(message: RenderingJobMessage) {
        val jobEntry = jobRepository.findByIdOrNull(ObjectId(message.id)) ?: return
        jobEntry.status = JobStatus.PROCESSING
        jobRepository.save(jobEntry)
        val file = resourceLoader.getResource("classpath:" + jobEntry.origin).file
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        cacheObject.size = file.length()
        try {
            this.storageImplementation.putTempFile(cacheObject, file.inputStream())
        } catch (exception: Exception) {
            jobEntry.status = JobStatus.FAILED
            jobRepository.save(jobEntry)
            return
        }
        jobEntry.status = JobStatus.FINISHED
        jobRepository.save(jobEntry)
        if (cacheObject.type == "image") {
            this.createImageJob(jobEntry, message)
        }
    }

    private fun createImageJob(jobEntry: RenderingJob, message: RenderingJobMessage) {
        message.missingQualities.forEach {
            val imageJob = SubJob(quality = it, parent = jobEntry)
            subJobRepository.save(imageJob)
        }
        amqpTemplate.convertAndSend(this.topicExchangeName, this.imageRoutingKey, SubJobMessage(jobEntry.id.toString()))
    }
}
