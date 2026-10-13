package org.edu_sharing.rendering.utils

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

/**
 * Startup sweep of orphaned job temp files (`app.temp.cleanup.*`), see [TempFileSweeper].
 *
 * @property enabled Whether the sweep runs at startup.
 * @property staleFileAge Only files older than this are removed. It must exceed the longest job duration of the
 *   pod (e.g. `app.converter.av.conversionTimeout`, default 30 min), because it is a plain age check: a younger
 *   file may still belong to a conversion of a sibling process sharing the same `/tmp`.
 */
@ConfigurationProperties(prefix = "app.temp.cleanup")
data class TempCleanupProperties(
    @DefaultValue("true") val enabled: Boolean,
    @DefaultValue("PT6H") val staleFileAge: Duration,
)
