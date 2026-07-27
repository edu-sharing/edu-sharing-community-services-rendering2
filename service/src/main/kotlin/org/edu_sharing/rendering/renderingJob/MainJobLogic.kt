package org.edu_sharing.rendering.renderingJob

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.metrics.RenderingMetrics
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class MainJobLogic (
    private val jobRepository: RenderingJobRepository,
    private val renderingMetrics: RenderingMetrics,
    ) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getMainJobEntry(jobId: String): RenderingJob? {
        log.debug("Looking up main job by id=$jobId")
        return jobRepository.findByIdOrNull(ObjectId(jobId))
    }

    fun getMainJobEntry(jobId: String, status: RenderingJobStatus): RenderingJob? {
        log.debug("Looking up main job by id=$jobId with status=$status")
        return jobRepository.findByIdAndStatus(ObjectId(jobId), status)
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
            renderingMetrics.recordJob(job, RenderingJobStatus.FAILED, emptyList())
            return true
        }
        val areSomeProcessingOrQueued = job.subJobs.any { it.status <= SubJobStatus.PROCESSING }
        if (areSomeProcessingOrQueued) {
            log.debug("Job $jobId still has sub jobs in QUEUED or PROCESSING state, deferring aggregation")
            return false
        }
        val areAllFinished = job.subJobs.all { it.status == SubJobStatus.FINISHED }
        // FAILED and TIMEOUT both count as "unsuccessful" here: an all-unsuccessful job is FAILED, a
        // mix of finished + unsuccessful is PARTIALLY_FAILED. This lets the stale-job reaper resolve a
        // timed-out job the same way an outright failure would.
        val areAllUnsuccessful = job.subJobs.all { it.status.isUnsuccessful }
        val jobStatus = if (areAllFinished) {
            RenderingJobStatus.FINISHED
        } else if (areAllUnsuccessful) {
            RenderingJobStatus.FAILED
        } else {
            RenderingJobStatus.PARTIALLY_FAILED
        }
        log.debug("Aggregated sub-job statuses for job $jobId: total=${job.subJobs.size}, resolvedStatus=$jobStatus")
        jobRepository.updateStatusWithoutVersion(job.id, status = jobStatus)
        renderingMetrics.recordJob(job, jobStatus, job.subJobs)
        return true
    }
}
