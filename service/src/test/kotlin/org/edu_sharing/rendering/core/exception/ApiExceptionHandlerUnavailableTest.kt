package org.edu_sharing.rendering.core.exception

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_MODULE_NOT_AVAILABLE
import org.edu_sharing.rendering.core.resilience.RemoteModuleUnavailableException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.time.Duration

class ApiExceptionHandlerUnavailableTest {
    private val handler = ApiExceptionHandler()
    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(ApiExceptionHandler::class.java) as Logger

    @BeforeEach
    fun attach() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detach() {
        logger.detachAppender(appender)
    }

    private fun assertSingleWarnWithoutStackTrace() {
        assertEquals(1, appender.list.size)
        val event = appender.list.single()
        assertEquals(Level.WARN, event.level)
        assertNull(event.throwableProxy, "no stack trace expected")
    }

    @Test
    fun `remote module unavailable is a 503 with Retry-After and the generic user message`() {
        val response = handler.handleRemoteModuleUnavailableException(
            RemoteModuleUnavailableException("circuit open", retryAfter = Duration.ofMillis(7200))
        )

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals("8", response.headers.getFirst(HttpHeaders.RETRY_AFTER))
        assertEquals(GENERIC_MODULE_NOT_AVAILABLE, response.body!!.userMessage)
        assertEquals(503, response.body!!.status)
        assertSingleWarnWithoutStackTrace()
    }

    @Test
    fun `remote module unavailable without a known duration has no Retry-After`() {
        val response = handler.handleRemoteModuleUnavailableException(RemoteModuleUnavailableException("bulkhead full"))

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertTrue(!response.headers.containsHeader(HttpHeaders.RETRY_AFTER))
        assertEquals(GENERIC_MODULE_NOT_AVAILABLE, response.body!!.userMessage)
    }

    @Test
    fun `a sub-second remaining open time is advertised as one second`() {
        val response = handler.handleRemoteModuleUnavailableException(
            RemoteModuleUnavailableException("open", retryAfter = Duration.ofMillis(10))
        )
        assertEquals("1", response.headers.getFirst(HttpHeaders.RETRY_AFTER))
    }

    @Test
    fun `gateway failures keep their status and use the generic user message`() {
        listOf(HttpStatus.BAD_GATEWAY, HttpStatus.GATEWAY_TIMEOUT).forEach { status ->
            appender.list.clear()
            val response = handler.handleModuleGatewayException(ModuleGatewayException(status, "lumi down"))

            assertEquals(status, response.statusCode)
            assertEquals(GENERIC_MODULE_NOT_AVAILABLE, response.body!!.userMessage)
            assertSingleWarnWithoutStackTrace()
        }
    }
}
