package org.edu_sharing.edusharingcommunityservicesdocumentconverter.io

import org.edu_sharing.edusharingcommunityservicesdocumentconverter.config.ConverterProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.AbstractResource
import org.springframework.stereotype.Component
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.Instant

/** Creates temp files in the converter work directory and sweeps stale ones at startup. */
@Component
class TempFiles(private val properties: ConverterProperties) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(TempFiles::class.java)

    val workDir: Path = (properties.workDir.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
        ?: Paths.get(System.getProperty("java.io.tmpdir"), "doc-converter"))
        .also { Files.createDirectories(it) }

    fun create(suffix: String): Path = Files.createTempFile(workDir, PREFIX, suffix)

    fun deleteQuietly(path: Path?) {
        if (path == null) return
        try {
            Files.deleteIfExists(path)
        } catch (e: Exception) {
            log.warn("Could not delete temp file {}: {}", path, e.message)
        }
    }

    /**
     * Removes our own temp files and abandoned LibreOffice profile directories (`.jodconverter_*`, created in
     * `java.io.tmpdir`) older than the configured age. Runs once at startup only: the profiles of the
     * processes started by this very run are brand new, so they are never matched.
     */
    override fun run(args: ApplicationArguments) {
        val cutoff = FileTime.from(Instant.now().minus(properties.staleFileAge))
        val tmp = Paths.get(System.getProperty("java.io.tmpdir"))
        sweep(workDir, { it.startsWith(PREFIX) }, cutoff)
        sweep(tmp, { it.startsWith(".jodconverter_") }, cutoff)
    }

    private fun sweep(dir: Path, matches: (String) -> Boolean, cutoff: FileTime) {
        try {
            Files.newDirectoryStream(dir).use { entries ->
                for (entry in entries) {
                    try {
                        if (!matches(entry.fileName.toString())) continue
                        if (Files.getLastModifiedTime(entry) >= cutoff) continue
                        entry.toFile().deleteRecursively()
                        log.info("Removed stale temp entry {}", entry)
                    } catch (e: Exception) {
                        log.warn("Could not sweep {}: {}", entry, e.message)
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Temp sweep of {} failed: {}", dir, e.message)
        }
    }

    companion object {
        const val PREFIX = "docconv-"
    }
}

/**
 * A file served once and deleted as soon as its stream is closed. Spring's resource converter always closes the
 * stream (also when the client aborts mid-transfer), which guarantees the cleanup.
 */
class DeleteOnCloseResource(private val path: Path, private val tempFiles: TempFiles) : AbstractResource() {
    private val length = Files.size(path)

    override fun getDescription() = "temp file [$path]"
    override fun getFilename(): String = path.fileName.toString()
    override fun contentLength(): Long = length
    override fun exists(): Boolean = Files.exists(path)

    override fun getInputStream(): InputStream {
        val delegate = try {
            Files.newInputStream(path)
        } catch (e: Exception) {
            tempFiles.deleteQuietly(path)
            throw e
        }
        return object : FilterInputStream(delegate) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    tempFiles.deleteQuietly(path)
                }
            }
        }
    }
}
