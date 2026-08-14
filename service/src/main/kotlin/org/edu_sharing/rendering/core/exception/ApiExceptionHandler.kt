package org.edu_sharing.rendering.core.exception

import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_ACCESS_DENIED
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_BAD_REQUEST
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_INTERNAL_SERVER_ERROR
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_MODULE_NOT_AVAILABLE
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_NOT_FOUND
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_OBJECT_NOT_SUPPORTED
import org.edu_sharing.rendering.core.dto.ErrorMessage
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.reactive.function.client.WebClientResponseException.Forbidden
import java.io.IOException

@ControllerAdvice
class ApiExceptionHandler {

    // ToDo error message builder: TraceId, class, stack, exception message

    private val log = LoggerFactory.getLogger(this.javaClass)

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFoundException(exception: EntryNotFoundException): ResponseEntity<ErrorMessage> {
        log.warn(exception.message, exception)
        val errorMessage = ErrorMessage(
            status = HttpStatus.NOT_FOUND.value(),
            message = exception.message,
            details = emptyMap(),
            exception = exception,
            userMessage = GENERIC_NOT_FOUND
        )
        return ResponseEntity(errorMessage, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgumentException(exception: IllegalArgumentException): ResponseEntity<ErrorMessage> {
        log.debug(exception.message, exception)
        val errorMessage = ErrorMessage(
            status = HttpStatus.BAD_REQUEST.value(),
            message = exception.message,
            exception = exception,
            userMessage = GENERIC_BAD_REQUEST,
            details = emptyMap(),
        )
        return ResponseEntity(errorMessage, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    fun handleModuleNotRegisteredException(exception: ModuleNotRegisteredException): ResponseEntity<ErrorMessage> {
        log.warn(exception.message, exception)
        val errorMessage = ErrorMessage(
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
            message = exception.message,
            exception = exception,
            userMessage = GENERIC_MODULE_NOT_AVAILABLE,
            details = emptyMap(),
        )
        return ResponseEntity(errorMessage, HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    fun handleObjectTypeNotSupportedException(exception: ObjectTypeNotSupportedException): ResponseEntity<ErrorMessage> {
        log.warn(exception.message, exception)
        val errorMessage = ErrorMessage(
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
            message = exception.message,
            exception = exception,
            userMessage = GENERIC_OBJECT_NOT_SUPPORTED,
            details = emptyMap(),
        )
        return ResponseEntity(errorMessage, HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleResourceNotFoundException(exception: ResourceNotFoundException): ResponseEntity<ErrorMessage> {
        log.warn(exception.message, exception)
        val errorMessage = ErrorMessage(
            status = HttpStatus.NOT_FOUND.value(),
            message = exception.message,
            details = emptyMap(),
            exception = exception,
            userMessage = GENERIC_NOT_FOUND
        )
        return ResponseEntity(errorMessage, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleAccessDeniedException(exception: AccessDeniedException): ResponseEntity<ErrorMessage> {
        log.debug(exception.message, exception)
        val errorMessage = ErrorMessage(
            status = HttpStatus.FORBIDDEN.value(),
            message = exception.message,
            details = emptyMap(),
            exception = exception,
            userMessage = GENERIC_ACCESS_DENIED
        )
        return ResponseEntity(errorMessage, HttpStatus.FORBIDDEN)
    }

    @ExceptionHandler
    fun handleGenericException(exception: Exception): ResponseEntity<ErrorMessage> {
        log.error(exception.message, exception)
        val errorMessage = ErrorMessage(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            message = exception.message,
            details = emptyMap(),
            exception = exception,
            userMessage = GENERIC_INTERNAL_SERVER_ERROR,
        )
        return ResponseEntity(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler
    fun handleIOException(exception: IOException): ResponseEntity<String> {
        if (exception.message?.contains("reset by peer") == true) {
            log.warn(exception.message, exception)
        } else {
            log.error(exception.message, exception)
        }
        return ResponseEntity(exception.message, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler
    fun handleForbiddenException(exception: Forbidden): ResponseEntity<Void> {
        log.error(exception.message, exception)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }
}
