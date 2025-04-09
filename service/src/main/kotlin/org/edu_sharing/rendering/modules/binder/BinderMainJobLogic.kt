package org.edu_sharing.rendering.modules.binder

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component


@Component
class BinderMainJobLogic(
    private val jobRepository: RenderingJobRepository,
) {

    private val log = LoggerFactory.getLogger(BinderMainJobLogic::class.java)

    /**
     * Function processMainJob
     *
     * Sets main job to failed if BOTH sub jobs did fail
     * Sets main job to finished if ONE sub job failed and the other finished
     * Does nothing otherwise
     */
    fun processMainJob(mainJobId: String) {
        try {
            var mainJob = jobRepository.findByIdOrNull(ObjectId(mainJobId))
                ?: throw IllegalStateException("Main job not found")
            val subJobStatus = mainJob.subJobs.map {it.status}
            if (subJobStatus.any { it < JobStatus.FINISHED }) {
                return
            }
            if (subJobStatus.toSet().size == 2 || subJobStatus.all {it == JobStatus.FINISHED }) {
                mainJob.status = JobStatus.FINISHED
            } else {
                mainJob.status = JobStatus.FAILED
            }
            jobRepository.save(mainJob)
        } catch (exception: Exception) {
            log.error("Error processing main job $mainJobId", exception)
        }
    }
}