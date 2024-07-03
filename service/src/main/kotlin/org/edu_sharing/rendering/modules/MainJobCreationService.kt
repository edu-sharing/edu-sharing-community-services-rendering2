package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.RenderingJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class MainJobCreationService(
    private val amqpTemplate: AmqpTemplate,
    private val renderingJobRepository: RenderingJobRepository,
    private val mapper: Mapper
) {
    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value("\${app.queue.job.key}")
    lateinit var jobRoutingKey: String

    fun createMainJob(cacheObject: CacheObject, module: RenderModules, missingQualities: List<Int> = emptyList()): String {
        val renderingJob = mapper.cacheObjectToRenderingJob(cacheObject, module)
        renderingJobRepository.save(renderingJob)
        val jobMessage = RenderingJobMessage(
            id = renderingJob.id.toString(),
            missingQualities = missingQualities
        )
        amqpTemplate.convertAndSend(this.topicExchangeName, this.jobRoutingKey, jobMessage)
        return renderingJob.id.toString()
    }

    fun getExistingJobId(cacheObject: CacheObject): String? {
        val existingJobs = renderingJobRepository.findAllByEsObjectId(cacheObject.nodeId)
        val unfinishedJob = existingJobs.firstOrNull {
            it.status != JobStatus.FINISHED && it.status != JobStatus.FAILED && it.esHash == cacheObject.hash
        }
        return if (unfinishedJob !== null) unfinishedJob.id.toString() else null
    }
}