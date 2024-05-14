package org.edu_sharing.rendering.processing.document

import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.RenderingJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.processing.MainJobLogic
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
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
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${edu_sharing.queue.document.name}", durable = "false"),
                exchange = Exchange(name = "\${edu_sharing.queue.topicExchange}", type = "topic"),
                key = ["\${edu_sharing.queue.document.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: RenderingJobMessage) {
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null) {
            logger.warn("Expected main job not found: " + message.id)
            return
        }
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        if (jobEntry.subJobs.isEmpty()) {
            logger.error("Document conversion failed for object ${jobEntry.esObjectId} due to missing sub job")
            return
        }
        val subJob = jobEntry.subJobs[0]
        subJob.status = JobStatus.PROCESSING
        subJobRepository.save(subJob)
        try {
            documentConversionService.convertAndMoveToCache(
                cacheObject,
                moduleRegistry.getRenderModule(jobEntry.module)
            )
            subJob.status = JobStatus.FINISHED
        } catch (exception: Exception) {
            logger.error("Document conversion failed for object ${jobEntry.esObjectId}", exception)
            subJob.status = JobStatus.FAILED
            subJob.message = "Conversion failed"
        }
        subJobRepository.save(subJob)
        mainJobLogic.processMainJob(jobEntry.id.toString())
    }
}