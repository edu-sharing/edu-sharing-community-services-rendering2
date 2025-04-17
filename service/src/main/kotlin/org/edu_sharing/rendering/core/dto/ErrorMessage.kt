package org.edu_sharing.rendering.core.dto

import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.PrintWriter
import java.io.StringWriter

class ErrorMessage (
    var status: Int? = null,
    message: String? = null,
    var details: Map<String, Any>,
    exception: Exception,
    var userMessage: String? = null,
) {
    companion object {
        private val verboseMessageLevels = listOf(
            Level.TRACE,
            Level.DEBUG,
            Level.INFO,
        )

        private val verboseStackLevels = listOf(
            Level.TRACE,
            Level.DEBUG
        )
    }
    private val log = LoggerFactory.getLogger(this.javaClass)

    var message: String? = if (verboseMessageLevels.any { log.isEnabledForLevel(it) }) message
        else "InvalidLogLevel: Log Level must be at least INFO for showing error messages"

    val logLevel = verboseMessageLevels.first { log.isEnabledForLevel(it) }.toString()

    val stacktrace = if (verboseStackLevels.any { log.isEnabledForLevel(it) }) {
        StringWriter().use {
            // ToDo add trace id if it exists
            exception.printStackTrace(PrintWriter(it))
            it.toString()
        }
    } else {
        null
    }
}
