package org.edu_sharing.rendering.utils

/**
 * Masks credentials in strings that end up in logs or exception messages, e.g. the request URI that
 * `WebClientException.message` embeds (`... for GET https://host/path?oauth_consumer_key=SECRET`).
 *
 * Handles three shapes:
 * - query/form parameters: `token=abc` -> `token=***`
 * - header-like pairs: `Authorization: Bearer abc` -> `Authorization: ***`
 * - JSON-ish pairs: `"password":"abc"` -> `"password":"***"`
 */
object LogSanitizer {
    private const val MASK = "***"

    private const val SENSITIVE_KEYS =
        "wstoken|password|token|oauth_consumer_key|authToken|apikey|api_key|access_token|refresh_token|client_secret|secret"

    /** `key=value` where the value runs to the next `&`, whitespace, quote, `)` or `]`. */
    private val QUERY_PARAM = Regex("(?i)\\b($SENSITIVE_KEYS)=[^&\\s\"')\\]]+")

    /** `"key":"value"` / `"key": "value"`. */
    private val JSON_PAIR = Regex("(?i)(\"(?:$SENSITIVE_KEYS)\"\\s*:\\s*)\"[^\"]*\"")

    /** `Authorization: ...`, `Cookie: ...`, `Set-Cookie: ...`, `X-Api-Key: ...` up to the end of the line. */
    private val HEADER_LINE =
        Regex("(?i)\\b(authorization|proxy-authorization|cookie|set-cookie|x-api-key)\\s*:\\s*[^\\r\\n]+")

    fun sanitize(text: String?): String? {
        if (text.isNullOrEmpty()) return text
        return text
            .replace(QUERY_PARAM) { "${it.groupValues[1]}=$MASK" }
            .replace(JSON_PAIR) { "${it.groupValues[1]}\"$MASK\"" }
            .replace(HEADER_LINE) { "${it.groupValues[1]}: $MASK" }
    }

    /**
     * A short, credential-free description of [throwable]: simple class name plus the sanitized
     * message (and the sanitized root cause message if it differs). No stack trace.
     */
    fun describe(throwable: Throwable): String {
        val head = "${throwable.javaClass.simpleName}: ${sanitize(throwable.message) ?: "(no message)"}"
        val root = generateSequence(throwable) { it.cause?.takeIf { cause -> cause !== it } }.last()
        return if (root !== throwable && root.message != throwable.message) {
            "$head (cause: ${root.javaClass.simpleName}: ${sanitize(root.message) ?: "(no message)"})"
        } else head
    }

    /**
     * Wraps [throwable] in a [SanitizedException] whose message is sanitized, preserving the stack
     * trace but dropping the original (message-leaking) cause chain. Use when an exception must be
     * logged with its stack trace but its message may contain a credential-bearing URI.
     */
    fun sanitize(throwable: Throwable): Throwable = SanitizedException(describe(throwable), throwable)

    class SanitizedException(message: String, original: Throwable) : RuntimeException(message, null, false, true) {
        init {
            stackTrace = original.stackTrace
        }
    }
}
