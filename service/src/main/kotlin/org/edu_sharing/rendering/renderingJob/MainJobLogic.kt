package org.edu_sharing.rendering.renderingJob

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class MainJobLogic (
    private val jobRepository: RenderingJobRepository,
    ) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getMainJobEntry(jobId: String): RenderingJob? {
        return jobRepository.findByIdOrNull(ObjectId(jobId))
    }

    fun processMainJob(jobId: String): Boolean {
        val job = getMainJobEntry(jobId)
        if (job == null) {
            log.error("Expected main job not found, job id: $jobId")
            return true
        }
        if (job.subJobs.isEmpty()) {
            log.error("No sub jobs found, job id: $jobId")
            jobRepository.updateStatusWithoutVersion(job.id, status = RenderingJobStatus.FAILED)
            return true
        }
        val areSomeProcessingOrQueued = job.subJobs.any { it.status <= SubJobStatus.PROCESSING }
        if (areSomeProcessingOrQueued) {
            return false
        }
        val areAllFinished = job.subJobs.all { it.status == SubJobStatus.FINISHED }
        val areAllFailed = job.subJobs.all { it.status == SubJobStatus.FAILED }
        val jobStatus = if (areAllFailed) {
            RenderingJobStatus.FAILED
        } else if(areAllFinished) {
            RenderingJobStatus.FINISHED
        } else {
            RenderingJobStatus.PARTIALLY_FAILED
        }
        jobRepository.updateStatusWithoutVersion(job.id, status = jobStatus)
        return true
    }
}
