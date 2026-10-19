package org.edu_sharing.rendering.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates the temp files of a job in `java.io.tmpdir` under one common name prefix ([PREFIX]), so that
 * [TempFileSweeper] can recognise and remove what a killed process (OOM, SIGKILL) left behind - without ever
 * touching files that belong to the JVM or to Tomcat. Every job-bound temp file must be created through here.
 */
object JobTempFiles {
    const val PREFIX = "rendering-"

    fun createFile(prefix: String, suffix: String?): File = File.createTempFile(PREFIX + prefix, suffix)

    fun createPath(prefix: String, suffix: String?): Path = Files.createTempFile(PREFIX + prefix, suffix)
}
