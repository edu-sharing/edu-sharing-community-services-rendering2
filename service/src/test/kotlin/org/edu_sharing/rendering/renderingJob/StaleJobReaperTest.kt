package org.edu_sharing.rendering.renderingJob

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.repository.StaleSubJobView
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class StaleJobReaperTest {

    private val subJobRepository: SubJobRepository = mockk(relaxed = true)
    private val mainJobLogic: MainJobLogic = mockk(relaxed = true)
    private val properties = JobReaperProperties().apply {
        defaultMaxProcessTime = Duration.ofMinutes(30)
        maxProcessTime = mutableMapOf("av_job" to Duration.ofMinutes(45))
    }
    private val reaper = StaleJobReaper(subJobRepository, mainJobLogic, properties)

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
}
