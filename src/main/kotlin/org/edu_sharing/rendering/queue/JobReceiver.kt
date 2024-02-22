package org.edu_sharing.rendering.queue

import org.bson.types.ObjectId
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.queue.RenderingJobMessage
import org.edu_sharing.rendering.dto.queue.SubJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class JobReceiver (
    @Qualifier("webApplicationContext") private val resourceLoader: ResourceLoader,
    private val mongoRepo: RenderingJobRepository,
    private val storageImplementation: StorageService,
    private val amqpTemplate: AmqpTemplate,
    ) {
    @Value("\${edu_sharing.video_resolutions}")
    lateinit var videoResolutions: List<Int>

    @Value("\${edu_sharing.image_sizes}")
    lateinit var imageSizes: List<Int>

    @Value("\${edu_sharing.imageRoutingKey}")
    lateinit var imageRoutingKey: String

    @Value("\${edu_sharing.topicExchangeName}")
    lateinit var topicExchangeName: String
    fun receiveMessage(message: RenderingJobMessage) {
        val jobEntry = mongoRepo.findByIdOrNull(ObjectId(message.id)) ?: return
        jobEntry.status = JobStatus.PROCESSING
        mongoRepo.save(jobEntry)
        val file = resourceLoader.getResource("classpath:" + jobEntry.origin).file
        val cacheObject = CacheObject(
            nodeId = jobEntry.esObjectId,
            type = jobEntry.esObjectType,
            size = file.length(),
            hash = jobEntry.esHash,
            mimeType = jobEntry.mimeType
        )
        this.storageImplementation.putTempFile(cacheObject, file.inputStream())
        jobEntry.status = JobStatus.FINISHED
        if (cacheObject.type == "image") {
            this.createImageJobs(jobEntry)
        }
    }

    private fun createImageJobs(jobEntry: RenderingJob) {
        imageSizes.forEach {
            val imageJob = SubJob(quality = it)
            jobEntry.subJobs.add(imageJob)
        }
        mongoRepo.save(jobEntry)
        amqpTemplate.convertAndSend(this.topicExchangeName, this.imageRoutingKey, SubJobMessage(jobEntry.id.toString()))
    }
}
