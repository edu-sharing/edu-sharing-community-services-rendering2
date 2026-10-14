package org.edu_sharing.rendering.modules

import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.net.ConnectException
import java.net.SocketException
import java.util.concurrent.TimeoutException

/** Translation keys of the repository UI for converter failures the user can act on. */
object ConverterErrorStrings {
    /** The converter rejected the document as such (empty, unreadable, not a valid notebook, ...). */
    const val DOCUMENT_INVALID = "RENDERING.ERROR.CONVERTER_DOCUMENT_INVALID"

    /** The document, or the result of its conversion, exceeds a size limit of the converter. */
    const val DOCUMENT_TOO_LARGE = "RENDERING.ERROR.CONVERTER_DOCUMENT_TOO_LARGE"
}

/**
 * A failed converter call, classified. Mirrors the "user-visible vs. operational" split of
 * [org.edu_sharing.rendering.modules.h5p.H5pImportFailure]: a bad or oversized document gets its own
 * [userMessage]; an unreachable or overloaded converter (and anything unexpected) gets the generic one and
 * the details stay in the log. The exception message is operational and free of credentials.
 */
class ConverterCallException(
    val userMessage: String,
    /** The document is to blame (a retry cannot help), not the converter or the network. */
    val documentProblem: Boolean,
    val status: Int?,
    message: String,
    cause: Throwable?
) : RuntimeException(message, cause) {

    companion object {
        private const val MAX_CAUSE_DEPTH = 10

        fun causes(throwable: Throwable): Sequence<Throwable> =
            generateSequence(throwable) { it.cause?.takeIf { cause -> cause !== it } }.take(MAX_CAUSE_DEPTH)

        fun statusOf(throwable: Throwable): Int? =
            causes(throwable).filterIsInstance<WebClientResponseException>().firstOrNull()?.statusCode?.value()

        /** Whether another attempt could succeed: converter busy/down (502/503/504) or a connection problem. */
        fun isTransient(throwable: Throwable): Boolean {
            val status = statusOf(throwable)
            if (status != null) return status in setOf(502, 503, 504)
            return causes(throwable).any {
                it is WebClientRequestException || it is ConnectException || it is SocketException ||
                    it is TimeoutException || it is io.netty.handler.timeout.TimeoutException ||
                    it is reactor.netty.http.client.PrematureCloseException ||
                    it is reactor.netty.channel.AbortedException
            }
        }

        fun of(throwable: Throwable, attempts: Int, path: String): ConverterCallException {
            val status = statusOf(throwable)
            val (userMessage, documentProblem) = when (status) {
                413 -> ConverterErrorStrings.DOCUMENT_TOO_LARGE to true
                400, 422 -> ConverterErrorStrings.DOCUMENT_INVALID to true
                else -> org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR to false
            }
            val kind = when {
                documentProblem -> "rejected the document"
                isTransient(throwable) -> "unavailable or overloaded"
                else -> "failed"
            }
            // The response exception message embeds the request URI, so describe() sanitizes it.
            val detail = org.edu_sharing.rendering.utils.LogSanitizer.describe(throwable)
            return ConverterCallException(
                userMessage, documentProblem, status,
                "Converter '$path' $kind (status=${status ?: "none"}, attempts=$attempts): $detail",
                throwable
            )
        }
    }
}
