package org.edu_sharing.rendering.modules.moodle

import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.util.LinkedMultiValueMap
import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 * A Moodle call failed in a way that may succeed on retry: HTTP 5xx, a connection problem or a timeout.
 * Client errors (4xx) and Moodle error objects are not transient.
 */
class MoodleTransientException(message: String) : IllegalStateException(message)

/** Default per-request timeout when the `timeout` credential is missing or not a positive number. */
internal const val DEFAULT_MOODLE_TIMEOUT_SECONDS = 30L

/** Reads the `timeout` credential (seconds); falls back to [DEFAULT_MOODLE_TIMEOUT_SECONDS]. */
internal fun moodleTimeout(credentials: Map<String, String>): Duration =
    Duration.ofSeconds(credentials["timeout"]?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_MOODLE_TIMEOUT_SECONDS)

/**
 * POSTs [form] as `application/x-www-form-urlencoded` and blocks for the answer body.
 *
 * Secrets (password, wstoken) belong in [form], never in [query]: query strings end up in access logs,
 * proxies and in the message of `WebClientException`. The body encoder percent-encodes every field as
 * UTF-8, so special characters in a password survive. [query] carries only non-secret routing parameters.
 *
 * Failures are mapped to credential-free exceptions without cause: [MoodleTransientException] for 5xx,
 * connect problems and timeouts, [IllegalStateException] for everything else.
 */
internal fun WebClient.postMoodleForm(
    path: String,
    query: Map<String, String>,
    form: Map<String, String?>,
    timeout: Duration,
    what: String,
): String? {
    val formData = LinkedMultiValueMap<String, String>()
    form.forEach { (key, value) -> formData.add(key, value ?: "") }
    return post()
        .uri { builder ->
            builder.path(path)
            query.forEach { (key, value) -> builder.queryParam(key, value) }
            builder.build()
        }
        .body(BodyInserters.fromFormData(formData))
        .retrieve()
        .bodyToMono<String>()
        .timeout(timeout)
        .onErrorMap { moodleTransportError(what, it) }
        .block()
}

/**
 * Replaces transport/HTTP exceptions with a readable one. The originals embed the full request URI in
 * their message, so they are neither kept as cause nor printed.
 */
internal fun moodleTransportError(what: String, e: Throwable): Throwable = when (e) {
    is WebClientResponseException -> {
        val message = "$what failed: Moodle answered with HTTP ${e.statusCode.value()}."
        if (e.statusCode.is5xxServerError) MoodleTransientException(message) else IllegalStateException(message)
    }
    is TimeoutException -> MoodleTransientException("$what timed out.")
    is WebClientRequestException ->
        MoodleTransientException("$what failed: Moodle not reachable (${e.cause?.javaClass?.simpleName ?: "I/O error"}).")
    is IllegalArgumentException, is IllegalStateException -> e
    else -> IllegalStateException("$what failed (${e.javaClass.simpleName}).")
}
