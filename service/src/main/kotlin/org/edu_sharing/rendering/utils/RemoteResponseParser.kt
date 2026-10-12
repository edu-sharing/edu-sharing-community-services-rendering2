package org.edu_sharing.rendering.utils

import tools.jackson.databind.ObjectMapper

/**
 * Parses the body of a remote API answer that was read as plain text.
 *
 * Reading the body as a String (instead of letting `bodyToMono<Dto>()` decode it) makes the reaction to a
 * misbehaving upstream readable: an HTML error page, an empty body, a wrong Content-Type or a JSON error
 * object produce a message naming the API and the start of the body - not an opaque `DecodingException`.
 * The excerpt is passed through [LogSanitizer], so credentials echoed by the upstream do not leak.
 */
object RemoteResponseParser {
    private const val EXCERPT_LENGTH = 100
    private val WHITESPACE = Regex("\\s+")

    fun <T> parse(body: String?, type: Class<T>, objectMapper: ObjectMapper, apiName: String): T {
        if (body.isNullOrBlank()) {
            throw IllegalStateException("$apiName returned an empty response")
        }
        return try {
            objectMapper.readValue(body, type)
        } catch (exception: Exception) {
            throw IllegalStateException(
                "$apiName returned an unexpected response (expected ${type.simpleName}, body starts with: " +
                    "${excerpt(body)})"
            )
        }
    }

    fun excerpt(body: String): String =
        LogSanitizer.sanitize(body.trim().replace(WHITESPACE, " ").take(EXCERPT_LENGTH)) ?: ""
}
