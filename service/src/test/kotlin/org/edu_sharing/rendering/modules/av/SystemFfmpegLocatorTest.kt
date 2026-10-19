package org.edu_sharing.rendering.modules.av

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SystemFfmpegLocatorTest {

    // The locator only decides how the process is started, so `sh -c nice` stands in for ffmpeg: it prints
    // the niceness it runs with.
    private fun nicenessOf(locator: SystemFfmpegLocator): Int =
        locator.createExecutor().use { process ->
            process.addArgument("-c")
            process.addArgument("nice")
            process.execute()
            process.inputStream.bufferedReader().readText().trim().toInt()
        }

    // The niceness a child of this JVM gets without the locator (0 unless the build itself runs niced).
    private fun inheritedNiceness(): Int =
        ProcessBuilder("sh", "-c", "nice").start().inputStream.bufferedReader().readText().trim().toInt()

    @Test
    fun startsFfmpegThroughNiceWhenNicenessIsSet() {
        assertEquals(minOf(inheritedNiceness() + 10, 19), nicenessOf(SystemFfmpegLocator("sh", 10)))
    }

    @Test
    fun startsFfmpegDirectlyWhenNicenessIsZero() {
        assertEquals(inheritedNiceness(), nicenessOf(SystemFfmpegLocator("sh", 0)))
    }
}
