package org.edu_sharing.edusharingcommunityservicesdocumentconverter.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.util.unit.DataSize
import java.time.Duration

/**
 * Typed settings of the conversion pipeline (`app.converter.*`).
 *
 * @property workDir Directory for the uploaded source and the converted result (temp files). Empty = a
 *   `doc-converter` sub directory of `java.io.tmpdir`.
 * @property staleFileAge Temp files / LibreOffice profile directories older than this are removed at startup
 *   (leftovers of a crashed previous run that shared the same temp volume).
 * @property htmlStyleScanWindow How much of the HTML result is scanned for `</head>` to inject the table CSS
 *   without parsing the whole document.
 * @property maxHtmlDomBytes Upper bound for the (memory hungry) Jsoup fallback used when no `</head>` is found
 *   in the scan window. Larger documents are rejected with 413 instead of risking an OutOfMemoryError.
 */
@ConfigurationProperties(prefix = "app.converter")
data class ConverterProperties(
    @DefaultValue("") val workDir: String = "",
    @DefaultValue("6h") val staleFileAge: Duration = Duration.ofHours(6),
    @DefaultValue("256KB") val htmlStyleScanWindow: DataSize = DataSize.ofKilobytes(256),
    @DefaultValue("32MB") val maxHtmlDomBytes: DataSize = DataSize.ofMegabytes(32),
    @DefaultValue val health: Health = Health()
) {
    /**
     * LibreOffice readiness check (`app.converter.health.*`).
     *
     * @property probeEnabled Run a tiny txt to pdf conversion in addition to the "manager is running" check.
     * @property probeTimeout Max time to wait for the probe. A timeout usually means all processes are busy.
     * @property cacheTtl How long a probe result is reused, so /health does not convert on every call.
     * @property failureThreshold Consecutive failed/timed-out probes before the component reports DOWN.
     */
    data class Health(
        @DefaultValue("true") val probeEnabled: Boolean = true,
        @DefaultValue("10s") val probeTimeout: Duration = Duration.ofSeconds(10),
        @DefaultValue("30s") val cacheTtl: Duration = Duration.ofSeconds(30),
        @DefaultValue("3") val failureThreshold: Int = 3
    )
}
