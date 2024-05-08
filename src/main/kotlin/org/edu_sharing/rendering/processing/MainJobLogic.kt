package org.edu_sharing.rendering.processing

import org.bson.types.ObjectId
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
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
            logger.warn("Expected main job not found: $jobId")
            return false
        }
        job.finishedTimestamp = System.currentTimeMillis()
        val areSomeProcessingOrQueued = job.subJobs.firstOrNull { it.status < JobStatus.FINISHED } != null
        if (areSomeProcessingOrQueued) {
            return false
        }
        val areAllFinished = job.subJobs.firstOrNull { it.status == JobStatus.FAILED } == null
        job.status = if (areAllFinished) JobStatus.FINISHED else JobStatus.FAILED
        jobRepository.save(job)
        return true
    }
}