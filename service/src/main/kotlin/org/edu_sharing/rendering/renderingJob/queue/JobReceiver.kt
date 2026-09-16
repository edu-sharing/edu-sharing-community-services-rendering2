package org.edu_sharing.rendering.renderingJob.queue

import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.ErrorStrings.ERROR_PROCESSING_JOB
import org.edu_sharing.rendering.core.annotation.ConditionalOnJobManager
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.modules.ConversionModule
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
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
                value = Queue(name = "#{jobQueueProperties.name}", durable = "true"),
                exchange = Exchange(name = "#{queueProperties.topicExchange}", type = "topic"),
                key = ["#{jobQueueProperties.key}"]
            )
        ], containerFactory = "queueListenerContainerFactory",
        concurrency = "#{jobQueueProperties.effectiveConcurrency}"
    )
    fun receiveMessage(message: RenderingJobMessage) {
        log.debug("Received job message: id=${message.id}, missingQualities=${message.missingQualities}")
        var jobEntry = jobRepository.findByIdOrNull(ObjectId(message.id)) ?: return
        // RabbitMQ is at-least-once (see AsyncAckDispatcher): a lost ack redelivers this message.
        // Guard against re-processing so a redelivery can't create a second set of sub-jobs.
        if (jobEntry.status >= RenderingJobStatus.FINISHED) {
            log.debug("Job ${jobEntry.id} already terminal (${jobEntry.status}); dropping redelivered message")
            return
        }
        if (jobEntry.subJobs.isNotEmpty()) {
            log.debug("Job ${jobEntry.id} already has ${jobEntry.subJobs.size} sub-job(s); skipping duplicate creation (redelivery)")
            return
        }
        try {
            log.debug("Transitioning job ${jobEntry.id} from ${jobEntry.status} to ${RenderingJobStatus.PROCESSING}")
            jobEntry.status = RenderingJobStatus.PROCESSING
            jobEntry.processingStartedTimestamp = System.currentTimeMillis()
            jobEntry = jobRepository.save(jobEntry)
            val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
            // Pass a stream supplier instead of an opened stream: the storage service (re-)invokes it
            // to get a fresh signed GET against the repository whenever it needs to re-read the
            // payload from the start (SDK retry, or the checksum signer's extra read-through) — see
            // `S3StorageService.putObjectStreaming`. That also means it — not this method — owns
            // closing every stream it obtains.
            if (jobEntry.conversionType) {
                log.debug("Job ${jobEntry.id} is conversion type, storing temp file for module ${jobEntry.module}")
                storageImplementation.putTempFile(cacheObject, { contentTransferService.getAsInputStream(cacheObject) })
            } else {
                log.debug("Job ${jobEntry.id} is non-conversion type, storing final object and marking FINISHED")
                storageImplementation.putObject(cacheObject, { contentTransferService.getAsInputStream(cacheObject) })
                jobEntry.status = RenderingJobStatus.FINISHED
                jobEntry.finishedTimestamp = System.currentTimeMillis()
                jobRepository.save(jobEntry)
                return
            }
            val renderModule = moduleRegistry.getRenderModule<RenderModule>(jobEntry.module)
            if (renderModule is ConversionModule) {
                log.debug("Delegating sub-job creation for job ${jobEntry.id} to module ${jobEntry.module}")
                renderModule.createConversionSubJobs(jobEntry, message)
            } else {
                log.warn("Render module ${jobEntry.module} does not implement the interface ConversionModule.")
                throw IllegalArgumentException("Render module ${jobEntry.module} does not implement the interface ConversionModule.")
            }
        } catch (e: Exception) {
            log.error("Error processing job ${jobEntry.id}", e)
            log.debug("Marking job ${jobEntry.id} as ${RenderingJobStatus.FAILED} after exception")
            jobEntry.status = RenderingJobStatus.FAILED
            jobEntry.finishedTimestamp = System.currentTimeMillis()
            jobEntry.errorMessage = ERROR_PROCESSING_JOB
            jobRepository.save(jobEntry)
        }
    }
}
