package org.edu_sharing.rendering.renderingJob

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.StaleSubJobView
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Times out orphaned rendering (sub-)jobs so a crashed/lost conversion, or a lost/never-delivered queue
 * message, cannot block a node forever.
 *
 * A sub-job left in PROCESSING (its consumer died mid-job, or an unhandled error nacked the message with
 * no redelivery) never becomes terminal, so its main job stays non-terminal — and
 * [MainJobCreationService.getExistingJobId] reuses any non-terminal job for the same node+hash, poisoning
 * re-rendering until the 8-day TTL. This reaper (master-only, scheduled, cluster-singleton via ShedLock)
 * runs three passes, each resolving to [MainJobLogic.processMainJob] so the main job lands on
 * FAILED/PARTIALLY_FAILED and unblocks `getExistingJobId` reuse. No re-queue — the frontend re-requests
 * (see the sodix/omega/ddb idempotency audit and [AsyncAckDispatcher]).
 *
 * 1. **PROCESSING sub-jobs** idle beyond their per-type [JobReaperProperties.maxProcessTimeFor] — the
 *    original crash-detection pass.
 * 2. **QUEUED sub-jobs** idle beyond their per-type [JobReaperProperties.maxQueuedTimeFor] — a safety net
 *    for a publish that never reached the broker, or a transient queue wiped by a broker/node restart
 *    (never redelivered, since nothing was ever really enqueued). Deliberately a *much* larger timeout
 *    than PROCESSING and per-type overridable: unlike PROCESSING, QUEUED is also the state of a
 *    legitimate backlog (e.g. HPA scale-up lag under a burst) — the default must clear any realistic
 *    backlog-drain time, or it reaps jobs that were never lost. (sodix/omega/ddb are labelled "import
 *    queues" elsewhere but do NOT need a per-type override here: each job is 1-2 fast REST calls
 *    resolving a link/reference, not a download, so even a large backlog drains in minutes.)
 * 3. **Main jobs stuck QUEUED with zero sub-jobs** — the job-queue message itself (the very first hop,
 *    [MainJobCreationService.createMainJob]) was lost before [JobReceiver][org.edu_sharing.rendering.renderingJob.queue.JobReceiver]
 *    ever ran to create sub-jobs, so pass 2 has nothing to find. `processMainJob` already resolves an
 *    empty-sub-jobs main job to FAILED, so this pass only needs to identify the candidates.
 */
@Component
@ConditionalOnMaster
@ConditionalOnProperty(name = ["app.jobreaper.enabled"], havingValue = "true", matchIfMissing = true)
class StaleJobReaper(
    private val subJobRepository: SubJobRepository,
    private val renderingJobRepository: RenderingJobRepository,
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
        val affectedJobs = mutableSetOf<ObjectId>()
        affectedJobs += reapStale(
            now, properties.minMaxProcessTime, properties::maxProcessTimeFor,
            subJobRepository::findProcessingSubJobsModifiedBefore, TIMEOUT_MESSAGE, "PROCESSING",
        )
        affectedJobs += reapStale(
            now, properties.minMaxQueuedTime, properties::maxQueuedTimeFor,
            subJobRepository::findQueuedSubJobsModifiedBefore, QUEUED_TIMEOUT_MESSAGE, "QUEUED",
        )
        affectedJobs += reapOrphanedQueuedJobs(now)
        reconcile(affectedJobs)
    }

    /**
     * Shared filter/timeout logic for both sub-job passes: [find] a coarse candidate set (pre-filtered
     * with [minTimeout], the smallest configured per-type timeout, so nothing stale under any type's
     * threshold is missed), refine per routing key via [timeoutFor], then bulk-transition the survivors
     * to TIMEOUT and return their parent job ids.
     */
    private fun reapStale(
        now: Instant,
        minTimeout: Duration,
        timeoutFor: (String) -> Duration,
        find: (Instant) -> List<StaleSubJobView>,
        message: String,
        statusLabel: String,
    ): Set<ObjectId> {
        val candidates = find(now.minus(minTimeout))
        val stale = candidates.filter { view ->
            val lastActivity = view.lastModifiedDate ?: return@filter false
            lastActivity.isBefore(now.minus(timeoutFor(view.routingKey)))
        }
        if (stale.isEmpty()) return emptySet()

        subJobRepository.timeoutSubJobs(stale.map { it.id }, message)
        val affectedJobs = stale.map { it.parent }.toSet()
        log.warn(
            "Stale-job reaper timed out ${stale.size} orphaned $statusLabel sub-job(s) exceeding max " +
                "$statusLabel time across ${affectedJobs.size} main job(s); reconciling"
        )
        return affectedJobs
    }

    /**
     * Main jobs stuck QUEUED with no sub-jobs at all — the job-queue message that would have triggered
     * [JobReceiver][org.edu_sharing.rendering.renderingJob.queue.JobReceiver] to create them was itself
     * lost. Uses the same generous [JobReaperProperties.defaultMaxQueuedTime] as the sub-job QUEUED pass
     * (there is no sub-job routing key to key a per-type override on yet).
     */
    private fun reapOrphanedQueuedJobs(now: Instant): Set<ObjectId> {
        val cutoff = now.minus(properties.defaultMaxQueuedTime)
        val orphaned = renderingJobRepository
            .findByStatusAndLastModifiedDateBefore(RenderingJobStatus.QUEUED, cutoff)
            .filter { it.subJobs.isEmpty() }
        if (orphaned.isEmpty()) return emptySet()
        log.warn(
            "Stale-job reaper found ${orphaned.size} main job(s) stuck QUEUED with no sub-jobs " +
                "(job-queue message lost or queue purged); reconciling to FAILED"
        )
        return orphaned.map { it.id }.toSet()
    }

    /**
     * processMainJob sees the TIMEOUT sub-jobs (or the empty sub-job list) as terminal and resolves each
     * main job (FAILED if all unsuccessful, PARTIALLY_FAILED if mixed), which unblocks getExistingJobId
     * reuse. Isolated per job so one failure doesn't abort the rest.
     */
    private fun reconcile(jobIds: Set<ObjectId>) {
        jobIds.forEach { jobId ->
            runCatching { mainJobLogic.processMainJob(jobId.toString()) }
                .onFailure { log.error("Failed to reconcile main job $jobId after timeout reaping", it) }
        }
    }

    companion object {
        private const val TIMEOUT_MESSAGE =
            "Rendering exceeded the maximum processing time and was timed out (no result produced)."
        private const val QUEUED_TIMEOUT_MESSAGE =
            "Rendering was queued but never started processing within the maximum wait time " +
                "(message lost or queue purged) and was timed out."
    }
}
