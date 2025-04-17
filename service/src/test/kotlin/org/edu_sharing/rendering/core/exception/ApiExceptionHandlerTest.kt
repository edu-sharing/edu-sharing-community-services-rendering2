package org.edu_sharing.rendering.core.exception

class ApiExceptionHandlerTest {
    /**
    @Test
    fun testHandleNotFoundException() {
        // Arrange
        val exceptionMessage = "Entry not found exception message"
        val exception = EntryNotFoundException(exceptionMessage)

        val apiExceptionHandler = ApiExceptionHandler()

        val expectedErrorMessage = ErrorMessage(
            status = HttpStatus.NOT_FOUND.value(),
            message = exceptionMessage,
            details = emptyMap(),
            exception = exception,
            userMessage = GENERIC_NOT_FOUND
        )
        val expectedResponse = ResponseEntity(expectedErrorMessage, HttpStatus.NOT_FOUND)

        // Act
        val actualResponse = apiExceptionHandler.handleNotFoundException(exception)

        // Assert
        assertEquals(expectedResponse, actualResponse)
    }

    @Test
    fun testHandleIllegalArgumentException() {
        // Arrange
        val exceptionMessage = "Illegal Argument exception message"
        val exception = IllegalArgumentException(exceptionMessage)

        val apiExceptionHandler = ApiExceptionHandler()

        val expectedErrorMessage = ErrorMessage(
            HttpStatus.BAD_REQUEST.value(),
            exceptionMessage
        )
        val expectedResponse = ResponseEntity(expectedErrorMessage, HttpStatus.BAD_REQUEST)

        // Act
        val actualResponse = apiExceptionHandler.handleIllegalArgumentException(exception)

        // Assert
        assertEquals(expectedResponse, actualResponse)
    }

    @Test
    fun testHandleModuleNotRegisteredException() {
        // Arrange
        val exception = ModuleNotRegisteredException("MYMODULE")

        val apiExceptionHandler = ApiExceptionHandler()

        val expectedErrorMessage = ErrorMessage(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
            "Module ${exception.message} not available"
        )
        val expectedResponse = ResponseEntity(expectedErrorMessage, HttpStatus.UNSUPPORTED_MEDIA_TYPE)

        // Act
        val actualResponse = apiExceptionHandler.handleModuleNotRegisteredException(exception)

        // Assert
        assertEquals(expectedResponse, actualResponse)
    }

    @Test
    fun testHandleObjectTypeNotSupportedException() {
        // Arrange
        val exceptionMessage = "Object Type Not Supported exception message"
        val exception = ObjectTypeNotSupportedException(exceptionMessage)

        val apiExceptionHandler = ApiExceptionHandler()

        val expectedErrorMessage = ErrorMessage(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
            "Unsupported object"
        )
        val expectedResponse = ResponseEntity(expectedErrorMessage, HttpStatus.UNSUPPORTED_MEDIA_TYPE)

        // Act
        val actualResponse = apiExceptionHandler.handleObjectTypeNotSupportedException(exception)

        // Assert
        assertEquals(expectedResponse, actualResponse)
    }

    @Test
    fun testHandleResourceNotFoundException() {
        // Arrange
        val exceptionMessage = "Resource not found exception message"
        val exception = ResourceNotFoundException(exceptionMessage)

        val apiExceptionHandler = ApiExceptionHandler()

        val expectedErrorMessage = ErrorMessage(
            HttpStatus.NOT_FOUND.value(),
            exceptionMessage
        )
        val expectedResponse = ResponseEntity(expectedErrorMessage, HttpStatus.NOT_FOUND)

        // Act
        val actualResponse = apiExceptionHandler.handleResourceNotFoundException(exception)

        // Assert
        assertEquals(expectedResponse, actualResponse)
    }

    @Test
    fun testHandleGenericException() {
        // Arrange
        val exceptionMessage = "A generic exception message"
        val exception = Exception(exceptionMessage)

        val apiExceptionHandler = ApiExceptionHandler()

        val expectedErrorMessage = ErrorMessage(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal server error"
        )
        val expectedResponse = ResponseEntity(expectedErrorMessage, HttpStatus.INTERNAL_SERVER_ERROR)

        // Act
        val actualResponse = apiExceptionHandler.handleGenericException(exception)

        // Assert
        assertEquals(expectedResponse, actualResponse)
    }
    */
}