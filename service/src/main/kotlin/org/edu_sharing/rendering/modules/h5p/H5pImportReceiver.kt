package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.utils.combinePath
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Argument
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * Second stage of the H5P job flow: imports the package into lumi. Only sub-jobs that missed the lookup stage
 * ([H5pLookupReceiver]) arrive here, and they arrive one at a time — see the queue binding below.
 */
@ConditionalOnConverter
@Component
class H5pImportReceiver(
    private val mainJobLogic: MainJobLogic,
    private val renderingJobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val h5pUploadService: H5pUploadService,
    private val mapper: Mapper,
    private val appInfo: AppInfo
){
    private val log = LoggerFactory.getLogger(H5pImportReceiver::class.java)

    @RabbitListener(
        bindings = [
            QueueBinding(
                // Single Active Consumer: lumi can import only one document at a time, so the
                // broker must route H5P jobs to exactly one consumer cluster-wide. Per-instance
                // concurrency=1 alone is not enough — with N pods, N competing consumers would
                // each process a job in parallel. x-single-active-consumer keeps a single consumer
                // active across all pods; the others stay on standby and take over only on failover.
                value = Queue(
                    name = "#{h5pQueueProperties.name}",
                    durable = "false",
                    arguments = [Argument(
                        name = "x-single-active-consumer",
                        value = "#{h5pQueueProperties.singleActiveConsumer}",
                        type = "java.lang.Boolean"
                    )]
                ),
                exchange = Exchange(name = "#{queueProperties.topicExchange}", type = "topic"),
                key = ["#{h5pQueueProperties.key}"]
            )
        ],
        containerFactory = "queueListenerContainerFactory",
        concurrency = "#{h5pQueueProperties.effectiveConcurrency}"
    )
    fun receiveMessage(message: RenderingJobMessage) {
        log.debug("H5P import message received: jobId={}", message.id)
        // The job is already PROCESSING when the lookup stage hands it over, so the redelivery guard keys on
        // the sub-job instead of the main job's status. An H5P job carries exactly one sub-job.
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(if (jobEntry == null) "No matching job entry with id {}"
            else "Job entry with id {} has no sub jobs" , message.id)
            return
        }
        log.debug("H5P job looked up: esObjectId={}, status={}", jobEntry.esObjectId, jobEntry.status)
        var subJob = jobEntry.subJobs.first()
        if (subJob.status != SubJobStatus.QUEUED) {
            log.debug("H5P job {} not queued for import (sub-job {}); dropping redelivered message",
                message.id, subJob.status)
            return
        }
        subJob.status = SubJobStatus.PROCESSING
        jobEntry.status = RenderingJobStatus.PROCESSING
        renderingJobRepository.save(jobEntry)
        subJob = subJobRepository.save(subJob)
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        log.debug("H5P calling upload service for nodeId={}", cacheObject.nodeId)
        try {
            val contentId = h5pUploadService.getContentId(cacheObject)
            log.info("H5P retrieval or upload successful. Content id: {}", contentId)
            subJob.status = SubJobStatus.FINISHED
            subJob.message = appInfo.public.url.combinePath(H5P_BASE_PATH, contentId)
        } catch (exception: Exception) {
            log.error("H5P retrieval or upload failed with error: {}", exception.message, exception)
            subJob.status = SubJobStatus.FAILED
            subJob.errorMessage = GENERIC_CONVERSION_ERROR
        }
        subJobRepository.save(subJob)
        mainJobLogic.processMainJob(message.id)
    }
}
