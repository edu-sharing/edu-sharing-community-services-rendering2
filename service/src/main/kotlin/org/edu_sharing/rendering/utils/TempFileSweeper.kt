package org.edu_sharing.rendering.utils

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.Instant

/**
 * Removes job temp files that a killed process left behind. `/tmp` is an emptyDir in the Helm chart and survives a
 * container restart, so after an OOM or SIGKILL the spool files of the interrupted jobs would stay forever.
 *
 * Deliberately conservative: it runs once at startup only (never periodically, so a running conversion is not hit),
 * and removes only REGULAR files directly in `java.io.tmpdir` whose name starts with [JobTempFiles.PREFIX] and that
 * are older than `app.temp.cleanup.stale-file-age`. Directories (`hsperfdata_*`, `tomcat.*`) and foreign names are
 * never touched. Files written by versions before the common prefix existed are not recognised.
 */
@Component
@EnableConfigurationProperties(TempCleanupProperties::class)
class TempFileSweeper(private val properties: TempCleanupProperties) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(TempFileSweeper::class.java)

    override fun run(args: ApplicationArguments) {
        if (!properties.enabled) return
        sweep(Paths.get(System.getProperty("java.io.tmpdir")), Instant.now())
    }

    /** @return the number of removed files */
    fun sweep(dir: Path, now: Instant): Int {
        val cutoff = FileTime.from(now.minus(properties.staleFileAge))
        var removed = 0
        try {
            Files.newDirectoryStream(dir).use { entries ->
                for (entry in entries) {
                    try {
                        if (!entry.fileName.toString().startsWith(JobTempFiles.PREFIX)) continue
                        if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) continue
                        if (Files.getLastModifiedTime(entry, LinkOption.NOFOLLOW_LINKS) >= cutoff) continue
                        if (Files.deleteIfExists(entry)) {
                            removed++
                            log.info("Removed stale temp file {}", entry)
                        }
                    } catch (e: Exception) {
                        log.warn("Could not sweep {}: {}", entry, e.message)
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Temp sweep of {} failed: {}", dir, e.message)
        }
        return removed
    }
}
