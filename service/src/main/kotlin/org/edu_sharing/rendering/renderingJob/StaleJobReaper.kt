package org.edu_sharing.rendering.renderingJob

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Times out orphaned rendering sub-jobs so a crashed/lost conversion cannot block a node forever.
 *
 * A sub-job left in PROCESSING (its consumer died mid-job, or an unhandled error nacked the message with
 * no redelivery) never becomes terminal, so its main job stays non-terminal — and
 * [MainJobCreationService.getExistingJobId] reuses any non-terminal job for the same node+hash, poisoning
 * re-rendering until the 8-day TTL. This reaper (master-only, scheduled, cluster-singleton via ShedLock)
 * finds PROCESSING sub-jobs idle beyond their per-type max process time, sets them to
 * [org.edu_sharing.rendering.renderingJob.entity.SubJobStatus.TIMEOUT], and re-runs
 * [MainJobLogic.processMainJob] to resolve the main job to FAILED/PARTIALLY_FAILED. No re-queue — the
 * frontend re-requests (see the sodix/omega/ddb idempotency audit and [AsyncAckDispatcher]).
 *
 * **Only PROCESSING is reaped, never QUEUED**: during an import backlog QUEUED sub-jobs wait legitimately
 * (queues can hold >100k), and a genuinely lost message is handled by broker redelivery + the 8-day TTL —
 * timing QUEUED out would kill legitimate load spikes.
 */
@Component
@ConditionalOnMaster
@ConditionalOnProperty(name = ["app.jobreaper.enabled"], havingValue = "true", matchIfMissing = true)
class StaleJobReaper(
    private val subJobRepository: SubJobRepository,
    private val mainJobLogic: MainJobLogic,
    private val properties: JobReaperProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = $$"${app.jobreaper.schedule}",
        initialDelayString = $$"${app.jobreaper.schedule}",
    )
    @SchedulerLock(name = "staleJobReaper", lockAtMostFor = "15m", lockAtLeastFor = "30s")
    fun reap() {
        val now = Instant.now()
        // Coarse pre-filter with the shortest configured timeout, then apply each routing key's own
        // timeout — nothing stale is missed since every per-type timeout is >= the minimum.
        val candidates = subJobRepository.findProcessingSubJobsModifiedBefore(now.minus(properties.minMaxProcessTime))
        val stale = candidates.filter { view ->
            val lastActivity = view.lastModifiedDate ?: return@filter false
            lastActivity.isBefore(now.minus(properties.maxProcessTimeFor(view.routingKey)))
        }
        if (stale.isEmpty()) return

        subJobRepository.timeoutSubJobs(stale.map { it.id }, TIMEOUT_MESSAGE)
        val affectedJobs = stale.map { it.parent }.toSet()
        log.warn(
            "Stale-job reaper timed out ${stale.size} orphaned PROCESSING sub-job(s) exceeding max " +
                "process time across ${affectedJobs.size} main job(s); reconciling"
        )
        // processMainJob now sees the TIMEOUT sub-jobs as terminal and resolves each main job (FAILED if
        // all unsuccessful, PARTIALLY_FAILED if mixed), which unblocks getExistingJobId reuse. Isolated
        // per job so one failure doesn't abort the rest.
        affectedJobs.forEach { jobId ->
            runCatching { mainJobLogic.processMainJob(jobId.toString()) }
                .onFailure { log.error("Failed to reconcile main job $jobId after timeout reaping", it) }
        }
    }

    companion object {
        private const val TIMEOUT_MESSAGE =
            "Rendering exceeded the maximum processing time and was timed out (no result produced)."
    }
}
