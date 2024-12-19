package org.edu_sharing.rendering.edusharingRepo

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.core.dto.ErrorMessage
import org.edu_sharing.rendering.edusharingRepo.dto.*
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationService
import org.edu_sharing.rendering.security.CorsService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.InvalidKeyException

@RestController
@RequestMapping("/admin")
@SecurityRequirement(name = "basicAuth")
@ConditionalOnMaster
@ConditionalOnProperty(name = ["app.repository.registration.enabled"], havingValue = "true")
class AdminController(
    private val repositoryRegistrationService: RepositoryRegistrationService,
    private val corsService: CorsService
) {

    @GetMapping("/security/cors/allowed_origins")
    fun getAllowedOrigins(): AllowedOriginsResult {
        return AllowedOriginsResult(corsService.getAllowedOrigins())
    }

    @GetMapping("/repository/register")
    fun registeredRepos(): List<RegistrationInfo> {
        return repositoryRegistrationService.getRegisteredRepositories()
            .map { toRegistrationInfo(it) }
    }

    @PostMapping("/repository/register")
    fun registerWithRepo(@RequestBody @Valid body: RegisterRepositoryRequest): RegistrationInfo {
        return toRegistrationInfo(repositoryRegistrationService.registerWithRepository(body))
    }

    @PatchMapping("/repository/register")
    fun updateRepoRegistration(@RequestBody @Valid body: RegisterRepositoryRequest): RegistrationInfo {
        return toRegistrationInfo(repositoryRegistrationService.registerWithRepository(body, true))
    }

    @DeleteMapping("/repository/register")
    fun deleteRepository(@RequestBody @Valid body: RemoveRepositoryRequest): RegistrationInfo {
        return toRegistrationInfo(repositoryRegistrationService.deleteRepository(body))
    }

    @PutMapping("/repository/modules/activate")
    fun activateOptionalModule(@RequestBody @Valid body: ActivateOptionalModuleRequest) {
        repositoryRegistrationService.activateOptionalModule(body)
    }

    @PutMapping("/repository/modules/deactivate")
    fun deactivateOptionalModule(@RequestBody @Valid body: DeactivateOptionalModuleRequest) {
        repositoryRegistrationService.deactivateOptionalModule(body)
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(InvalidKeyException::class)
    fun handleInvalidKeyException(exception: InvalidKeyException): ResponseEntity<ErrorMessage> {
        val message = ErrorMessage(
            HttpStatus.NOT_FOUND.value(),
            exception.message ?: ""
        )

        return ResponseEntity(message, HttpStatus.NOT_FOUND)
    }

    private fun toRegistrationInfo(entity: RepositoryRegistration): RegistrationInfo {
        return RegistrationInfo(
            repoId = entity.repoId,
            url = entity.url,
            publicKey = entity.publicKey,
            domains = entity.domains ?: emptyList()
        )
    }

}
