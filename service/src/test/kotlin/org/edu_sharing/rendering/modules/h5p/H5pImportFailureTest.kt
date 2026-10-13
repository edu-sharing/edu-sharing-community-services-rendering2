package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.core.ErrorStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.concurrent.TimeoutException

class H5pImportFailureTest {

    private fun lumiAnswered(status: Int, body: String = "") =
        WebClientResponseException.create(status, "status $status", HttpHeaders.EMPTY, body.toByteArray(), null)

    @Test
    fun `a package over a size limit is reported as too large`() {
        val failure = H5pImportFailure.of(
            lumiAnswered(413, "package-validation-failed:total-size-too-large (max: 1 GB, used: 1.38 GB)")
        )

        assertEquals(ErrorStrings.H5P_PACKAGE_TOO_LARGE, failure.userMessage)
        assertTrue(failure.packageProblem)
        assertEquals(413, failure.status)
        assertEquals("package-validation-failed:total-size-too-large (max: 1 GB, used: 1.38 GB)", failure.detail)
    }

    @Test
    fun `a package lumi rejects as invalid is reported as invalid`() {
        for (status in listOf(400, 422)) {
            val failure = H5pImportFailure.of(lumiAnswered(status, "not-in-whitelist"))

            assertEquals(ErrorStrings.H5P_PACKAGE_INVALID, failure.userMessage, "status $status")
            assertTrue(failure.packageProblem, "status $status")
        }
    }

    @Test
    fun `operational failures of lumi keep the generic message`() {
        for (status in listOf(500, 503, 504, 507)) {
            val failure = H5pImportFailure.of(lumiAnswered(status, "boom"))

            assertEquals(ErrorStrings.GENERIC_CONVERSION_ERROR, failure.userMessage, "status $status")
            assertFalse(failure.packageProblem, "status $status")
            assertEquals(status, failure.status)
        }
    }

    @Test
    fun `an answer from lumi is found behind wrapping exceptions`() {
        val wrapped = RuntimeException("wrapper", IllegalStateException("inner", lumiAnswered(413)))

        assertEquals(ErrorStrings.H5P_PACKAGE_TOO_LARGE, H5pImportFailure.of(wrapped).userMessage)
    }

    @Test
    fun `failures without an answer from lumi are generic`() {
        for (exception in listOf<Throwable>(
            RuntimeException("wrapper", TimeoutException("no answer")),
            java.net.ConnectException("connection refused"),
            NullPointerException()
        )) {
            val failure = H5pImportFailure.of(exception)

            assertEquals(ErrorStrings.GENERIC_CONVERSION_ERROR, failure.userMessage)
            assertFalse(failure.packageProblem)
            assertNull(failure.status)
            assertNull(failure.detail)
        }
    }

    @Test
    fun `lumi's explanation is cut to a sensible length and blank bodies are dropped`() {
        assertEquals(500, H5pImportFailure.of(lumiAnswered(422, "x".repeat(5_000))).detail?.length)
        assertNull(H5pImportFailure.of(lumiAnswered(422, "   ")).detail)
    }
}
