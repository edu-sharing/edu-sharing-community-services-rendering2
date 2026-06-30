package org.edu_sharing.rendering.modules.av

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ws.schild.jave.Encoder
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Bounds how long a single AV (audio/video) ffmpeg encode may run. If [runEncode]'s block
 * exceeds the configured timeout, the supplied encoder's ffmpeg process is aborted, which makes
 * [Encoder.encode] throw and lets the receiver's existing failure handling mark the sub-job
 * FAILED. The encoder must be the per-conversion (prototype) instance so the abort only ever
 * targets that conversion's process — safe under any consumer concurrency.
 */
@Component
@ConditionalOnAvConverter
class AvConversionTimeoutGuard(
    @param:Value($$"${app.converter.av.conversionTimeout}") private val timeout: Duration
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Dedicated daemon scheduler — independent of the shared taskScheduler; watchdog tasks are
    // instantaneous (they only call abortEncoding), so a small pool scales fine.
    private val scheduler = Executors.newScheduledThreadPool(2) { runnable ->
        Thread(runnable, "av-timeout-watchdog").apply { isDaemon = true }
    }

    /** Runs [block] (the ffmpeg encode); if it exceeds the timeout, aborts THIS [encoder]. */
    fun runEncode(encoder: Encoder, subJobId: Any?, block: () -> Unit) {
        val watchdog = scheduler.schedule({
            log.warn("AV conversion timeout ($timeout) exceeded; aborting ffmpeg for subJobId=$subJobId")
            encoder.abortEncoding()
        }, timeout.toMillis(), TimeUnit.MILLISECONDS)
        try {
            block()
        } finally {
            watchdog.cancel(false)
        }
    }

    @PreDestroy
    fun shutdown() {
        scheduler.shutdownNow()
    }
}
