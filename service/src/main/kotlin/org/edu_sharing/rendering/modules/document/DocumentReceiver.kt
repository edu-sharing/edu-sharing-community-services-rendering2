package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@ConditionalOnConverter
@Component
class DocumentReceiver (
    private val mainJobLogic: MainJobLogic,
    private val mapper: Mapper,
    private val documentConversionService: DocumentConversionService,
    private val subJobRepository: SubJobRepository,
    private val moduleRegistry: ModuleRegistry
){
    companion object {
        const val PUBLIC_FAILURE_MESSAGE = "Conversion failed"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${app.queue.document.name}", durable = "false"),
                exchange = Exchange(name = "\${app.queue.topicExchange}", type = "topic"),
                key = ["\${app.queue.document.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: RenderingJobMessage) {
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(if (jobEntry == null) "No job entry with id {}"
            else "Job entry with id {} has no sub jobs" , message.id)
            return
        }
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        val subJob = jobEntry.subJobs[0]
        subJob.status = JobStatus.PROCESSING
        subJobRepository.save(subJob)
        try {
            documentConversionService.convertAndMoveToCache(
                cacheObject,
                moduleRegistry.getRenderModule(jobEntry.module)
            )
            subJob.status = JobStatus.FINISHED
        } catch (_: Exception) {
            log.error("Document conversion failed for object ${jobEntry.esObjectId}")
            subJob.status = JobStatus.FAILED
            subJob.message = PUBLIC_FAILURE_MESSAGE
        }
        subJobRepository.save(subJob)
        mainJobLogic.processMainJob(jobEntry.id.toString())
    }
}