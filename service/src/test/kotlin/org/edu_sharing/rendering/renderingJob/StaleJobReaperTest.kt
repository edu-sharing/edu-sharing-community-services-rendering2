package org.edu_sharing.rendering.renderingJob

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.StaleSubJobView
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class StaleJobReaperTest {

    private val subJobRepository: SubJobRepository = mockk(relaxed = true)
    private val renderingJobRepository: RenderingJobRepository = mockk(relaxed = true)
    private val mainJobLogic: MainJobLogic = mockk(relaxed = true)
    private val properties = JobReaperProperties().apply {
        defaultMaxProcessTime = Duration.ofMinutes(30)
        maxProcessTime = mutableMapOf("av_job" to Duration.ofMinutes(45))
        defaultMaxQueuedTime = Duration.ofHours(6)
        // A hypothetical future queue with a legitimately longer backlog-drain time - not a real override
        // shipped in application.properties today (see JobReaperProperties.maxQueuedTime doc).
        maxQueuedTime = mutableMapOf("bulk_import_job" to Duration.ofDays(7))
    }
    private val reaper = StaleJobReaper(subJobRepository, renderingJobRepository, mainJobLogic, properties)

    @Test
    fun `times out only PROCESSING sub-jobs past their per-type max process time and reconciles their parents`() {
        val now = Instant.now()
        val parentA = ObjectId()
        val parentB = ObjectId()

        // default-type sub-job idle 40min > 30min default → stale
        val staleDefault = StaleSubJobView(ObjectId(), "document_job", now.minus(Duration.ofMinutes(40)), parentA)
        // av sub-job idle 40min < 45min av override → NOT stale (per-type timeout beats the default)
        val freshAv = StaleSubJobView(ObjectId(), "av_job", now.minus(Duration.ofMinutes(40)), parentB)
        // av sub-job idle 50min > 45min → stale
        val staleAv = StaleSubJobView(ObjectId(), "av_job", now.minus(Duration.ofMinutes(50)), parentB)

        every { subJobRepository.findProcessingSubJobsModifiedBefore(any()) } returns
            listOf(staleDefault, freshAv, staleAv)

        reaper.reap()

        val idsSlot = slot<Collection<ObjectId>>()
        verify(exactly = 1) { subJobRepository.timeoutSubJobs(capture(idsSlot), any()) }
        assert(idsSlot.captured.toSet() == setOf(staleDefault.id, staleAv.id)) {
            "expected only the two overdue sub-jobs to be timed out, got ${idsSlot.captured}"
        }
        verify(exactly = 1) { mainJobLogic.processMainJob(parentA.toString()) }
        verify(exactly = 1) { mainJobLogic.processMainJob(parentB.toString()) }
    }

    @Test
    fun `does nothing when no sub-job exceeds its timeout`() {
        val now = Instant.now()
        val fresh = StaleSubJobView(ObjectId(), "document_job", now.minus(Duration.ofMinutes(5)), ObjectId())
        every { subJobRepository.findProcessingSubJobsModifiedBefore(any()) } returns listOf(fresh)

        reaper.reap()

        verify(exactly = 0) { subJobRepository.timeoutSubJobs(any(), any()) }
        verify(exactly = 0) { mainJobLogic.processMainJob(any()) }
    }

    @Test
    fun `reconciles a shared parent only once when it has multiple stale sub-jobs`() {
        val now = Instant.now()
        val parent = ObjectId()
        val s1 = StaleSubJobView(ObjectId(), "document_job", now.minus(Duration.ofMinutes(40)), parent)
        val s2 = StaleSubJobView(ObjectId(), "image_job", now.minus(Duration.ofMinutes(40)), parent)
        every { subJobRepository.findProcessingSubJobsModifiedBefore(any()) } returns listOf(s1, s2)

        reaper.reap()

        val idsSlot = slot<Collection<ObjectId>>()
        verify(exactly = 1) { subJobRepository.timeoutSubJobs(capture(idsSlot), any()) }
        assert(idsSlot.captured.toSet() == setOf(s1.id, s2.id))
        verify(exactly = 1) { mainJobLogic.processMainJob(parent.toString()) }
    }

    @Test
    fun `times out QUEUED sub-jobs past their per-type max queued time, respecting a longer per-type override`() {
        val now = Instant.now()
        val parentA = ObjectId()
        val parentB = ObjectId()

        // default-type sub-job idle 7h > 6h default -> stale
        val staleDefault = StaleSubJobView(ObjectId(), "document_job", now.minus(Duration.ofHours(7)), parentA)
        // bulk_import_job sub-job idle 1 day < 7 day override -> NOT stale (legitimate long backlog)
        val freshBulkImport = StaleSubJobView(ObjectId(), "bulk_import_job", now.minus(Duration.ofDays(1)), parentB)
        every { subJobRepository.findQueuedSubJobsModifiedBefore(any()) } returns listOf(staleDefault, freshBulkImport)

        reaper.reap()

        val idsSlot = slot<Collection<ObjectId>>()
        verify(exactly = 1) { subJobRepository.timeoutSubJobs(capture(idsSlot), any()) }
        assert(idsSlot.captured.toSet() == setOf(staleDefault.id)) {
            "expected only the overdue default-type sub-job to be timed out, got ${idsSlot.captured}"
        }
        verify(exactly = 1) { mainJobLogic.processMainJob(parentA.toString()) }
        verify(exactly = 0) { mainJobLogic.processMainJob(parentB.toString()) }
    }

    @Test
    fun `reconciles a main job stuck QUEUED with no sub-jobs at all`() {
        val orphan = RenderingJob(
            module = "image",
            esObjectType = "ccm:io",
            esObjectId = "node-1",
            repoId = "repo-1",
            esHash = "hash-1",
            mimeType = "image/png",
            nodeVersion = "1",
            status = RenderingJobStatus.QUEUED,
        )
        every { renderingJobRepository.findByStatusAndLastModifiedDateBefore(RenderingJobStatus.QUEUED, any()) } returns
            listOf(orphan)

        reaper.reap()

        verify(exactly = 0) { subJobRepository.timeoutSubJobs(any(), any()) }
        verify(exactly = 1) { mainJobLogic.processMainJob(orphan.id.toString()) }
    }

    @Test
    fun `does not reconcile a QUEUED main job that already has sub-jobs`() {
        val subJob = SubJob(routingKey = "document_job", parent = mockk(relaxed = true))
        val jobWithSubJobs = RenderingJob(
            module = "document",
            esObjectType = "ccm:io",
            esObjectId = "node-2",
            repoId = "repo-1",
            esHash = "hash-2",
            mimeType = "application/pdf",
            nodeVersion = "1",
            status = RenderingJobStatus.QUEUED,
            subJobs = mutableListOf(subJob),
        )
        every { renderingJobRepository.findByStatusAndLastModifiedDateBefore(RenderingJobStatus.QUEUED, any()) } returns
            listOf(jobWithSubJobs)

        reaper.reap()

        verify(exactly = 0) { mainJobLogic.processMainJob(jobWithSubJobs.id.toString()) }
    }
}
