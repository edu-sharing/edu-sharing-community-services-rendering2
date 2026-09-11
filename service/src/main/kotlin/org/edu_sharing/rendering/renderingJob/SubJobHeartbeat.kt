package org.edu_sharing.rendering.renderingJob

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Keeps a sub-job's `lastModifiedDate` fresh while a single, legitimately long-running blocking call is
 * in flight (a Moodle restore poll, a document/jupyter/H5P converter call, an EduHTML archive
 * extraction) — so [StaleJobReaper]'s PROCESSING pass, which times out a sub-job idle beyond its max
 * process time, can't mistake it for an orphaned/crashed one.
 *
 * [org.edu_sharing.rendering.modules.av.AvConversionListener] gets this for free today (ffmpeg progress
 * ticks already trigger a real `save()` every 10%); every other converter runs one opaque blocking call
 * with nothing in between to bump the timestamp, hence this generic wrapper instead of a per-module
 * bespoke mechanism.
 *
 * A lightweight side channel, not the real work: a dedicated background thread ticks
 * [SubJobRepository.touchLastModifiedDate] every [interval] until [block] returns or throws, then stops.
 * [DEFAULT_INTERVAL] is comfortably under every [JobReaperProperties] `maxProcessTimeFor` default
 * (>= `PT30M`).
 */
@Component
class SubJobHeartbeat(
    private val subJobRepository: SubJobRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun <T> run(subJobId: ObjectId, interval: Duration = DEFAULT_INTERVAL, block: () -> T): T {
        val executor = Executors.newSingleThreadScheduledExecutor()
        executor.scheduleWithFixedDelay(
            {
                runCatching { subJobRepository.touchLastModifiedDate(subJobId) }
                    .onFailure { log.warn("Heartbeat touch failed for sub-job $subJobId", it) }
            },
            interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS
        )
        try {
            return block()
        } finally {
            // Not shutdown(): block() already returned/threw, so no further tick must run - shutdownNow
            // interrupts a tick that might be mid-flight without waiting for its next scheduled delay.
            executor.shutdownNow()
        }
    }

    companion object {
        private val DEFAULT_INTERVAL: Duration = Duration.ofMinutes(5)
    }
}
