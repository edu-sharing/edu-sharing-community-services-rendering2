package org.edu_sharing.rendering.renderingJob

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class MainJobCreationService(
    private val amqpTemplate: AmqpTemplate,
    private val renderingJobRepository: RenderingJobRepository,
    private val mapper: Mapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value($$"${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value($$"${app.queue.job.key}")
    lateinit var jobRoutingKey: String

    fun createMainJob(
        cacheObject: CacheObject,
        module: String,
        missingQualities: Collection<Int> = emptyList(),
        isConversionType: Boolean = false
    ): String {
        log.debug(
            "Creating main job for nodeId={}, module={}, conversionType={}, missingQualities={}",
            cacheObject.nodeId,
            module,
            isConversionType,
            missingQualities
        )
        val renderingJob = mapper.cacheObjectToRenderingJob(cacheObject, module, isConversionType)
        // Participate in the activeJobPerNodeHash unique index (one active job per node+hash).
        renderingJob.deduplicated = true
        renderingJobRepository.save(renderingJob)
        val jobMessage = RenderingJobMessage(
            id = renderingJob.id.toString(),
            missingQualities = missingQualities
        )
        log.debug(
            "Publishing job message id={} to exchange={} with routingKey={}",
            renderingJob.id,
            topicExchangeName,
            jobRoutingKey
        )
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, jobMessage)
        return renderingJob.id.toString()
    }

    fun getExistingJobId(cacheObject: CacheObject): String? {
        val existingJobs = renderingJobRepository.findAllByEsObjectId(cacheObject.nodeId)
        log.debug("Found ${existingJobs.size} existing job(s) for nodeId=${cacheObject.nodeId}")
        val unfinishedJob = existingJobs.firstOrNull {
            it.status != RenderingJobStatus.FINISHED && it.status != RenderingJobStatus.FAILED && it.status != RenderingJobStatus.PARTIALLY_FAILED && it.esHash == cacheObject.hash
        }
        return if (unfinishedJob !== null) unfinishedJob.id.toString() else null
    }

    fun retrieveOrCreateJob(cacheObject: CacheObject, module: RenderModule): String =
        retrieveOrCreateDeduplicatedJob(
            findActiveJobId = {
                getExistingJobId(cacheObject)?.also {
                    log.debug("Reusing existing job id=$it for nodeId=${cacheObject.nodeId}")
                }
            },
            create = {
                log.debug("No reusable job found for nodeId=${cacheObject.nodeId}, creating new job with module=${module.module()}")
                createMainJob(cacheObject = cacheObject, module = module.module(), isConversionType = true)
            }
        )
}
