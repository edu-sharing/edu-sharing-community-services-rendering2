package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.slf4j.LoggerFactory

abstract class AbstractReceiver(
    private val mainJobLogic: MainJobLogic,
    private val mapper: Mapper,
    private val conversionService: ConversionService
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    protected fun processMessage(message: RenderingJobMessage) {
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(
                if (jobEntry == null) "No job entry with id {}"
                else "Job entry with id {} has no sub jobs", message.id
            )
            return
        }
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        conversionService.process(cacheObject, jobEntry)
        mainJobLogic.processMainJob(jobEntry.id.toString())
    }

}