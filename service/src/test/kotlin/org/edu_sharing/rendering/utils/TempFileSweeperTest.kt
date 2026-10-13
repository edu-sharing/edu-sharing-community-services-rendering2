package org.edu_sharing.rendering.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant

class TempFileSweeperTest {

    @TempDir
    lateinit var dir: Path

    private val now = Instant.now()
    private val sweeper = TempFileSweeper(TempCleanupProperties(true, Duration.ofHours(6)))

    private fun file(name: String, age: Duration): Path =
        Files.createFile(dir.resolve(name)).also { Files.setLastModifiedTime(it, FileTime.from(now.minus(age))) }

    @Test
    fun removesOnlyOldRegularFilesWithTheJobPrefix() {
        val oldJob = file("${JobTempFiles.PREFIX}abc123.tmp", Duration.ofHours(7))
        val freshJob = file("${JobTempFiles.PREFIX}def456.tmp", Duration.ofMinutes(5))
        val oldForeign = file("hsperfdata-like.tmp", Duration.ofDays(3))
        val oldDirectory = Files.createDirectory(dir.resolve("${JobTempFiles.PREFIX}dir"))
        Files.setLastModifiedTime(oldDirectory, FileTime.from(now.minus(Duration.ofDays(3))))
        Files.createFile(oldDirectory.resolve("inner.tmp"))

        val removed = sweeper.sweep(dir, now)

        assertEquals(1, removed)
        assertTrue(Files.notExists(oldJob))
        assertTrue(Files.exists(freshJob))
        assertTrue(Files.exists(oldForeign))
        assertTrue(Files.exists(oldDirectory.resolve("inner.tmp")))
    }

    @Test
    fun jobTempFilesCarryThePrefixTheSweeperLooksFor() {
        val created = JobTempFiles.createFile("x_", ".tmp")
        val createdPath = JobTempFiles.createPath("y-", ".tmp")
        try {
            assertTrue(created.name.startsWith(JobTempFiles.PREFIX))
            assertTrue(createdPath.fileName.toString().startsWith(JobTempFiles.PREFIX))
        } finally {
            created.delete()
            Files.deleteIfExists(createdPath)
        }
    }

    @Test
    fun aMissingDirectoryDoesNotThrow() {
        assertEquals(0, sweeper.sweep(dir.resolve("missing"), now))
    }
}
