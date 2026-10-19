package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.slf4j.LoggerFactory

abstract class AbstractReceiver(
    private val mainJobLogic: MainJobLogic,
    private val mapper: Mapper,
    private val conversionService: ConversionService,
    private val renderingJobRepository: RenderingJobRepository
) {
    companion object {
        const val PUBLIC_FAILURE_MESSAGE = "Conversion failed"
    }

    private val log = LoggerFactory.getLogger(this.javaClass)

    protected fun processMessage(message: RenderingJobMessage) {
        log.debug("Received message for job id='{}'", message.id)
        var jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(
                if (jobEntry == null) "No job entry with id {}"
                else "Job entry with id {} has no sub jobs", message.id
            )
            return
        }
        // RabbitMQ is at-least-once: a redelivery for a job whose sub-jobs are all terminal must not convert
        // again. A sub-job left PROCESSING by the dead delivery is deliberately restarted by the service.
        if (jobEntry.subJobs.all { it.status >= SubJobStatus.FINISHED }) {
            log.debug("Job id='{}' has only terminal sub-jobs; dropping redelivered message", message.id)
            // Idempotent: resolves a main job whose previous delivery died before aggregating.
            mainJobLogic.processMainJob(message.id)
            return
        }
        log.debug("Job id='{}' found with {} sub-job(s), transitioning to status PROCESSING", message.id, jobEntry.subJobs.size)
        jobEntry.status = RenderingJobStatus.PROCESSING
        jobEntry.processingStartedTimestamp = System.currentTimeMillis()
        jobEntry = renderingJobRepository.save(jobEntry)
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        log.debug("Dispatching job id='{}' to ConversionService.process()", message.id)
        conversionService.process(cacheObject, jobEntry)
        mainJobLogic.processMainJob(message.id)
    }

}