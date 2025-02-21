package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
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

    protected fun processMessage(message: RenderingJobMessage, async: Boolean) {
        var jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(
                if (jobEntry == null) "No job entry with id {}"
                else "Job entry with id {} has no sub jobs", message.id
            )
            return
        }
        jobEntry.status = JobStatus.PROCESSING
        jobEntry = renderingJobRepository.save(jobEntry)
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        conversionService.process(cacheObject, jobEntry)
        if (!async) {
            mainJobLogic.processMainJob(message.id)
        }
    }

}