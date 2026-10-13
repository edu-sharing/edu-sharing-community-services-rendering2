package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.SubJobHeartbeat
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
import java.time.Instant

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
    private val appInfo: AppInfo,
    private val subJobHeartbeat: SubJobHeartbeat
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
                    durable = "true",
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
        subJob.processingStartedDate = Instant.now()
        jobEntry.status = RenderingJobStatus.PROCESSING
        jobEntry.processingStartedTimestamp = System.currentTimeMillis()
        renderingJobRepository.save(jobEntry)
        subJob = subJobRepository.save(subJob)
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        log.debug("H5P calling upload service for nodeId={}", cacheObject.nodeId)
        try {
            // The upload call can legitimately run up to the per-repo H5P timeout (5 min default),
            // well inside the reaper's PT30M default but a heartbeat is what keeps that true if
            // either value ever changes.
            val contentId = subJobHeartbeat.run(subJob.id) { h5pUploadService.getContentId(cacheObject) }
            log.info("H5P retrieval or upload successful. Content id: {}", contentId)
            subJob.status = SubJobStatus.FINISHED
            subJob.finishedDate = Instant.now()
            subJob.message = appInfo.public.url.combinePath(H5P_BASE_PATH, contentId)
        } catch (exception: Exception) {
            val failure = H5pImportFailure.of(exception)
            if (failure.packageProblem) {
                // lumi refused the package itself (too large, not a valid H5P package): not an error of the
                // system and nothing a stack trace would add to.
                log.warn(
                    "H5P package of nodeId={} rejected by lumi (status {}): {}",
                    cacheObject.nodeId, failure.status, failure.detail
                )
            } else {
                log.error("H5P retrieval or upload failed with error: {}", exception.message, exception)
            }
            subJob.status = SubJobStatus.FAILED
            subJob.finishedDate = Instant.now()
            subJob.errorMessage = failure.userMessage
            // The client shows the main job's message: a failed sub-job is left out of the job info, so its own
            // message never reaches it (see JobInfoService), and the aggregation only sets the status.
            // Set before the status flips, so a client that sees FAILED sees the message too. Not a save() of
            // jobEntry - see updateErrorMessageWithoutVersion - and never allowed to keep the job from ending:
            // an exception here once left the sub-job in PROCESSING for good.
            try {
                renderingJobRepository.updateErrorMessageWithoutVersion(jobEntry.id, failure.userMessage)
            } catch (updateException: Exception) {
                log.error("Could not set the error message of job {}: {}", jobEntry.id, updateException.message, updateException)
            }
        }
        subJobRepository.save(subJob)
        mainJobLogic.processMainJob(message.id)
    }
}
