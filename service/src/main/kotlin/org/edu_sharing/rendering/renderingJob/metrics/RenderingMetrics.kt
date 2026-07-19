package org.edu_sharing.rendering.renderingJob.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Fachliche Micrometer-Instrumente für die Job-/Converter-Bewertung unter Last. Bewusst an einer
 * einzigen Stelle ([org.edu_sharing.rendering.renderingJob.MainJobLogic.processMainJob]) gespeist:
 * dort erreicht jeder Job seinen terminalen Status und alle Sub-Jobs tragen ihre persistierten
 * Timestamps ([SubJob.createdDate] / [SubJob.lastModifiedDate]) — so deckt eine Aufrufstelle jedes
 * Converter-Modul ab, ohne die einzelnen Receiver anzufassen.
 *
 * Emittierte Meter (Prefix `rendering.`, sichtbar unter `/actuator/prometheus`):
 * - `rendering.job.duration{module,outcome}` — End-to-End-Dauer (Erstellung → terminaler Status).
 * - `rendering.subjob.duration{module,quality,outcome}` — Dauer einer Einzel-Konvertierung.
 * - `rendering.job.failed{module,outcome}` / `rendering.subjob.failed{module,quality}` — Fehler.
 */
@Component
class RenderingMetrics(private val meterRegistry: MeterRegistry) {

    /**
     * Erfasst Job- und Sub-Job-Metriken, sobald ein Job terminal ([RenderingJobStatus.FINISHED],
     * [RenderingJobStatus.FAILED], [RenderingJobStatus.PARTIALLY_FAILED]) aufgelöst ist.
     */
    fun recordJob(job: RenderingJob, status: RenderingJobStatus, subJobs: List<SubJob>) {
        val outcome = status.name
        val durationMs = (System.currentTimeMillis() - job.creationTimestamp).coerceAtLeast(0)
        Timer.builder("rendering.job.duration")
            .description("End-to-end rendering job duration from creation to terminal status")
            .tag("module", job.module)
            .tag("outcome", outcome)
            .publishPercentileHistogram()
            .register(meterRegistry)
            .record(Duration.ofMillis(durationMs))

        if (status != RenderingJobStatus.FINISHED) {
            meterRegistry.counter("rendering.job.failed", "module", job.module, "outcome", outcome)
                .increment()
        }

        subJobs.forEach { recordSubJob(job.module, it) }
    }

    private fun recordSubJob(module: String, subJob: SubJob) {
        val created = subJob.createdDate?.toInstant()
        val finished = subJob.lastModifiedDate
        val quality = subJob.quality.toString()
        val outcome = subJob.status.name

        if (created != null && finished != null) {
            Timer.builder("rendering.subjob.duration")
                .description("Single sub-job (per-quality) conversion duration")
                .tag("module", module)
                .tag("quality", quality)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Duration.between(created, finished).coerceAtLeast(Duration.ZERO))
        }

        if (subJob.status == SubJobStatus.FAILED) {
            meterRegistry.counter("rendering.subjob.failed", "module", module, "quality", quality)
                .increment()
        }
    }
}
