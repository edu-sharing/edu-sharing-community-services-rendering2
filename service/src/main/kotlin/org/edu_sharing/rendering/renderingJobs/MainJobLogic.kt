package org.edu_sharing.rendering.renderingJobs

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJobs.entity.JobStatus
import org.edu_sharing.rendering.renderingJobs.entity.RenderingJob
import org.edu_sharing.rendering.renderingJobs.repository.RenderingJobRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class MainJobLogic (
    private val jobRepository: RenderingJobRepository,
    ) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun getMainJobEntry(jobId: String): RenderingJob? {
        return jobRepository.findByIdOrNull(ObjectId(jobId))
    }

    fun processMainJob(jobId: String): Boolean {
        val job = getMainJobEntry(jobId)
        if (job == null) {
            logger.error("Expected main job not found, job id: $jobId")
            return true
        }
        if (job.subJobs.isEmpty()) {
            logger.error("No sub jobs found, job id: $jobId")
            job.status = JobStatus.FAILED
            jobRepository.save(job)
            return true
        }
        val areSomeProcessingOrQueued = job.subJobs.firstOrNull { it.status < JobStatus.FINISHED } != null
        if (areSomeProcessingOrQueued) {
            return false
        }
        val areAllFinished = job.subJobs.firstOrNull { it.status == JobStatus.FAILED } == null
        job.status = if (areAllFinished) JobStatus.FINISHED else JobStatus.FAILED
        job.finishedTimestamp = System.currentTimeMillis()
        jobRepository.save(job)
        return true
    }
}