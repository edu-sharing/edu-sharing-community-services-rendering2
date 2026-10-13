package org.edu_sharing.rendering.core.exception

import org.springframework.http.HttpStatus
import java.time.Duration

/**
 * A synchronous call to a backing module (e.g. the lumi proxy) failed because the module is unreachable or too
 * slow: [status] is 502 (connection error) or 504 (timeout). Mapped by [ApiExceptionHandler] to a single WARN line
 * (no stack trace) and the user message `GENERIC_MODULE_NOT_AVAILABLE`.
 */
class ModuleGatewayException(
    val status: HttpStatus,
    message: String,
    cause: Throwable? = null,
    val retryAfter: Duration? = null,
) : RuntimeException(message, cause, false, false)
