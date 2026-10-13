package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.core.ErrorStrings
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * What a failed call to lumi means for the render job.
 *
 * lumi answers a package it cannot import with a status that says whose fault it is: 413 if the package
 * exceeds a size limit, 400/422 if it is not a valid H5P package. Another attempt cannot change any of that,
 * and it is something the user can act on, so those get their own message. Everything else (503/504/507, a
 * timeout, a connection error, a plain 500) is an operational problem - the user gets the generic message and
 * the details go to the log.
 *
 * The message is a translation key of the repository UI (see [ErrorStrings]).
 */
data class H5pImportFailure(
    val userMessage: String,
    /** The package is to blame, not lumi or the network. */
    val packageProblem: Boolean,
    /** HTTP status of lumi's answer, if there was one. */
    val status: Int?,
    /** lumi's own explanation (its response body), if there was one. */
    val detail: String?
) {
    companion object {
        private const val MAX_DETAIL = 500
        private const val MAX_CAUSE_DEPTH = 10

        fun of(exception: Throwable): H5pImportFailure {
            // Mono.block() wraps checked exceptions (a TimeoutException, say), so look through the causes.
            val response = generateSequence(exception) { it.cause }
                .take(MAX_CAUSE_DEPTH)
                .filterIsInstance<WebClientResponseException>()
                .firstOrNull()
            val status = response?.statusCode?.value()
            val detail = response?.responseBodyAsString?.take(MAX_DETAIL)?.ifBlank { null }
            return when (status) {
                413 -> H5pImportFailure(ErrorStrings.H5P_PACKAGE_TOO_LARGE, true, status, detail)
                400, 422 -> H5pImportFailure(ErrorStrings.H5P_PACKAGE_INVALID, true, status, detail)
                else -> H5pImportFailure(ErrorStrings.GENERIC_CONVERSION_ERROR, false, status, detail)
            }
        }
    }
}
