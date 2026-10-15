package org.edu_sharing.edusharingcommunityservicesdocumentconverter.health

import org.edu_sharing.edusharingcommunityservicesdocumentconverter.config.ConverterProperties
import org.jodconverter.core.document.DefaultDocumentFormatRegistry
import org.jodconverter.core.office.OfficeManager
import org.jodconverter.local.LocalConverter
import org.slf4j.LoggerFactory
import org.springframework.boot.health.contributor.AbstractHealthIndicator
import org.springframework.boot.health.contributor.Health
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Readiness component "libreOffice": the office manager must be running and, optionally, a tiny txt to pdf
 * conversion must succeed. The probe result is cached so /health does not convert on every call.
 *
 * A single timeout is not treated as failure (all processes may simply be busy with real conversions); only
 * `failureThreshold` consecutive failures/timeouts flip the component to DOWN. Liveness stays independent of
 * this component (see the health group configuration in application.properties).
 */
@Component("libreOffice")
class LibreOfficeHealthIndicator(
    private val officeManager: OfficeManager,
    private val converter: LocalConverter,
    properties: ConverterProperties
) : AbstractHealthIndicator("LibreOffice health check failed") {
    private val log = LoggerFactory.getLogger(LibreOfficeHealthIndicator::class.java)
    private val config = properties.health
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "libreoffice-probe").apply { isDaemon = true } }

    private var cachedAt = 0L
    private var cachedResult: ProbeResult? = null
    private var consecutiveFailures = 0

    private data class ProbeResult(val ok: Boolean, val detail: String)

    @Synchronized
    override fun doHealthCheck(builder: Health.Builder) {
        if (!officeManager.isRunning) {
            builder.down().withDetail("reason", "office manager is not running")
            return
        }
        if (!config.probeEnabled) {
            builder.up().withDetail("probe", "disabled")
            return
        }
        val now = System.nanoTime()
        val cached = cachedResult
        val result = if (cached != null && now - cachedAt < config.cacheTtl.toNanos()) {
            cached
        } else {
            runProbe().also {
                cachedResult = it
                cachedAt = System.nanoTime()
                consecutiveFailures = if (it.ok) 0 else consecutiveFailures + 1
            }
        }
        builder.withDetail("probe", result.detail).withDetail("consecutiveFailures", consecutiveFailures)
        if (result.ok || consecutiveFailures < config.failureThreshold) builder.up() else builder.down()
    }

    private fun runProbe(): ProbeResult {
        val future = executor.submit<Int> {
            val out = ByteArrayOutputStream()
            converter.convert(ByteArrayInputStream("health".toByteArray()))
                .`as`(DefaultDocumentFormatRegistry.getFormatByExtension("txt")!!)
                .to(out)
                .`as`(DefaultDocumentFormatRegistry.getFormatByExtension("pdf")!!)
                .execute()
            out.size()
        }
        return try {
            val size = future.get(config.probeTimeout.toMillis(), TimeUnit.MILLISECONDS)
            if (size > 0) ProbeResult(true, "txt->pdf ok") else ProbeResult(false, "txt->pdf produced no output")
        } catch (e: TimeoutException) {
            future.cancel(true)
            log.warn("LibreOffice probe timed out after {} (processes busy or hung)", config.probeTimeout)
            ProbeResult(false, "probe timed out after ${config.probeTimeout}")
        } catch (e: Exception) {
            log.warn("LibreOffice probe failed: {}", e.message)
            ProbeResult(false, "probe failed: ${e.cause?.message ?: e.message}")
        }
    }
}
