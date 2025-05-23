package org.edu_sharing.rendering.renderingJob.queue

import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.ErrorStrings.ERROR_PROCESSING_JOB
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.modules.ConversionModule
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.ConditionalOnJobManager
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component


@ConditionalOnJobManager
@Component
class JobReceiver(
    private val jobRepository: RenderingJobRepository,
    private val storageImplementation: StorageService,
    private val mapper: Mapper,
    private val contentTransferService: ContentTransferService,
    private val moduleRegistry: ModuleRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${app.queue.job.name}", durable = "false"),
                exchange = Exchange(name = "\${app.queue.topicExchange}", type = "topic"),
                key = ["\${app.queue.job.key}"]
            )
        ]
    )
    fun receiveMessage(message: RenderingJobMessage) {
        var jobEntry = jobRepository.findByIdOrNull(ObjectId(message.id)) ?: return
        try {
            jobEntry.status = RenderingJobStatus.PROCESSING
            jobEntry = jobRepository.save(jobEntry)
            val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
            if (jobEntry.conversionType) {
                storageImplementation.putTempFile(cacheObject, contentTransferService.getAsInputStream(cacheObject))
            } else {
                storageImplementation.putObject(cacheObject, contentTransferService.getAsInputStream(cacheObject))
                jobEntry.status = RenderingJobStatus.FINISHED
                jobRepository.save(jobEntry)
                return
            }
            val renderModule = moduleRegistry.getRenderModule<RenderModule>(jobEntry.module)
            if (renderModule is ConversionModule) {
                renderModule.createConversionSubJobs(jobEntry, message)
            } else {
                log.warn("Render module ${jobEntry.module} does not implement the interface ConversionModule.")
                throw IllegalArgumentException("Render module ${jobEntry.module} does not implement the interface ConversionModule.")
            }
        } catch (exception: Exception) {
            jobEntry.status = RenderingJobStatus.FAILED
            jobEntry.finishedTimestamp = System.currentTimeMillis()
            jobEntry.errorMessage = ERROR_PROCESSING_JOB
            jobRepository.save(jobEntry)
        }
    }
}
