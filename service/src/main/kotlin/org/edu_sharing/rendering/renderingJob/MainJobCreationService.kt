package org.edu_sharing.rendering.renderingJob

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
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

    fun createMainJob(
        cacheObject: CacheObject,
        module: String,
        missingQualities: List<Int> = emptyList(),
        isConversionType: Boolean = false
    ): String {
        val renderingJob = mapper.cacheObjectToRenderingJob(cacheObject, module, isConversionType)
        renderingJobRepository.save(renderingJob)
        val jobMessage = RenderingJobMessage(
            id = renderingJob.id.toString(),
            missingQualities = missingQualities
        )
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, jobMessage)
        return renderingJob.id.toString()
    }

    fun getExistingJobId(cacheObject: CacheObject): String? {
        val existingJobs = renderingJobRepository.findAllByEsObjectId(cacheObject.nodeId)
        val unfinishedJob = existingJobs.firstOrNull {
            it.status != JobStatus.FINISHED && it.status != JobStatus.FAILED && it.esHash == cacheObject.hash
        }
        return if (unfinishedJob !== null) unfinishedJob.id.toString() else null
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: RenderModule): String {
        val existingJobId = getExistingJobId(cacheObject)
        if (existingJobId != null) {
            return existingJobId
        }

        return createMainJob(
            cacheObject = cacheObject,
            module = module.module(),
            isConversionType = true
        )
    }
}