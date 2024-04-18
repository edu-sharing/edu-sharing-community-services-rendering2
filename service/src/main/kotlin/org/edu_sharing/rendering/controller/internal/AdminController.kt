package org.edu_sharing.rendering.controller.internal

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.edu_sharing.rendering.dto.ErrorMessage
import org.edu_sharing.rendering.service.RepositoryRegistrationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.security.InvalidKeyException

@RestController
@RequestMapping("/admin")
@SecurityRequirement(name = "basicAuth")
@ConditionalOnProperty(name = ["edu_sharing.registration.enabled"], havingValue = "true")
class AdminController(
    private val repositoryRegistrationService: RepositoryRegistrationService
) {
    @PutMapping("/repository/register")
    fun registerWithRepo() {
        repositoryRegistrationService.registerWithRepository()
    }

    @PatchMapping("/repository/publicKey")
    fun updatePublicRepositoryKey(){
        repositoryRegistrationService.updatePublicRepositoryKey()
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(InvalidKeyException::class)
    fun handleInvalidKeyException(exception : InvalidKeyException) : ResponseEntity<ErrorMessage> {
        val message = ErrorMessage(
            HttpStatus.NOT_FOUND.value(),
            exception.message
        )

        return ResponseEntity(message, HttpStatus.NOT_FOUND)
    }

}