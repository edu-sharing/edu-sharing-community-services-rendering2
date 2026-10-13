package org.edu_sharing.rendering.integration

import org.edu_sharing.rendering.core.ErrorStrings
import org.edu_sharing.rendering.modules.h5p.H5pImportReceiver
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

/**
 * The failure path of the H5P import against a real MongoDB. A unit test with mocked repositories cannot see what
 * these show: [RenderingJob.version] is a `val`, so `save()` returns a *new* instance and leaves the one the receiver
 * holds at its old version - a second `save()` of it fails with an optimistic locking error. The receiver used to
 * do exactly that to attach the message to the main job, which left the job in PROCESSING forever.
 *
 * lumi is not running here, so the upload fails like any operational failure.
 */
class H5pImportFailureIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var receiver: H5pImportReceiver

    @Autowired
    lateinit var jobs: RenderingJobRepository

    @Autowired
    lateinit var subJobs: SubJobRepository

    private fun queuedJob(): Pair<RenderingJob, SubJob> {
        val job = jobs.save(
            RenderingJob(
                module = "H5P", esObjectType = "ccm:io", esObjectId = "node-${UUID.randomUUID()}", repoId = "repoid",
                esHash = "hash", mimeType = "application/zip", nodeVersion = "1.0",
                status = RenderingJobStatus.QUEUED, conversionType = true
            )
        )
        val subJob = subJobs.save(SubJob(routingKey = "h5p", parent = job, status = SubJobStatus.QUEUED))
        return job to subJob
    }

    @Test
    fun `a failed import finishes the job as failed and gives it the message the client shows`() {
        val (job, subJob) = queuedJob()

        receiver.receiveMessage(RenderingJobMessage(job.id.toString()))

        val savedSubJob = subJobs.findById(subJob.id).orElseThrow()
        val savedJob = jobs.findById(job.id).orElseThrow()
        assertEquals(SubJobStatus.FAILED, savedSubJob.status, "the sub-job must not stay PROCESSING")
        assertEquals(RenderingJobStatus.FAILED, savedJob.status, "the job must not stay PROCESSING")
        assertEquals(ErrorStrings.GENERIC_CONVERSION_ERROR, savedSubJob.errorMessage)
        assertEquals(ErrorStrings.GENERIC_CONVERSION_ERROR, savedJob.errorMessage)
    }
}
