package org.edu_sharing.rendering.queue

import org.bson.types.ObjectId
import org.edu_sharing.rendering.dto.queue.SubJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class AvReceiver (
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val imageJobContainer: SimpleMessageListenerContainer
){
    private val logger = LoggerFactory.getLogger(javaClass)
    fun receiveMessage(message: SubJobMessage) {
        imageJobContainer.stop()
        val jobEntry = jobRepository.findByIdOrNull(ObjectId(message.id))
        if (jobEntry == null) {
            logger.warn("Expected main job not found: " + message.id)
            return
        }
        val subJob = jobEntry.subJobs.first {it.quality == message.quality}
        subJob.status = JobStatus.PROCESSING
        subJobRepository.save(subJob)
        imageJobContainer.start()
    }
}