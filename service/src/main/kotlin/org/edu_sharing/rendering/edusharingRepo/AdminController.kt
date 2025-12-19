package org.edu_sharing.rendering.edusharingRepo

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.edu_sharing.rendering.cacheCleaner.TrackingEntryRepository
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_INTERNAL_SERVER_ERROR
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.core.dto.ErrorMessage
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.EntryNotFoundException
import org.edu_sharing.rendering.edusharingRepo.cors.CorsSyncService
import org.edu_sharing.rendering.edusharingRepo.dto.*
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationService
import org.edu_sharing.rendering.storage.StorageService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.InvalidKeyException
import kotlin.math.abs

@RestController
@RequestMapping("/admin")
@SecurityRequirement(name = "basicAuth")
@ConditionalOnMaster
@ConditionalOnProperty(name = ["app.repository.registration.enabled"], havingValue = "true")
class AdminController(
    private val repositoryRegistrationService: RepositoryRegistrationService,
    private val corsSyncService: CorsSyncService,
    private val storageService: StorageService,
    private val trackingEntryRepository: TrackingEntryRepository,
    private val mapper: Mapper,
    private val trackingService: TrackingService,
) {

    @GetMapping("/repository/register")
    fun registeredRepos(): List<RegistrationInfo> {
        return repositoryRegistrationService.getRegisteredRepositories()
            .map { toRegistrationInfo(it) }
    }

    @PostMapping("/repository/register")
    fun registerWithRepo(@RequestBody @Valid body: RegisterRepositoryRequest): RegistrationInfo {
        val registration = repositoryRegistrationService.registerWithRepository(body)
        corsSyncService.syncAllowedOriginsWithRepository(registration)
        corsSyncService.triggerSync()
        return toRegistrationInfo(registration)
    }

    @PatchMapping("/repository/register")
    fun updateRepoRegistration(@RequestBody @Valid body: RegisterRepositoryRequest): RegistrationInfo {
        return toRegistrationInfo(repositoryRegistrationService.registerWithRepository(body, true))
    }

    @DeleteMapping("/repository/register")
    fun deleteRepository(@RequestBody @Valid body: RemoveRepositoryRequest): RegistrationInfo {
        val result = toRegistrationInfo(repositoryRegistrationService.deleteRepository(body))
        corsSyncService.triggerSync()
        return result
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
            status = HttpStatus.NOT_FOUND.value(),
            message = exception.message ?: "",
            details = emptyMap(),
            exception = exception,
            userMessage = GENERIC_INTERNAL_SERVER_ERROR,
        )

        return ResponseEntity(message, HttpStatus.NOT_FOUND)
    }

    @DeleteMapping("/cache/remove")
    fun deleteObjectFromCache(
        @RequestParam repoId: String,
        @RequestParam nodeId: String,
        @RequestParam(required = false) hash: String?
    ): ResponseEntity<Void> {
        if (hash != null) {
            val entry = trackingEntryRepository.findByRepoIdAndNodeIdAndHash(repoId, nodeId, hash).orElseThrow {
                throw EntryNotFoundException("No tracking entry found for repoId $repoId, nodeId $nodeId and hash $hash.")
            }
            val cacheObject = mapper.trackingEntryToCacheObject(entry)
            storageService.removeObject(cacheObject)
        } else {
            val entries = trackingEntryRepository.findAllByRepoIdAndNodeId(repoId, nodeId)
            if (entries.isEmpty()) {
                throw EntryNotFoundException("No tracking entries found for repoId $repoId, nodeId $nodeId.")
            }
            entries.forEach {
                val cacheObject = mapper.trackingEntryToCacheObject(it)
                storageService.removeObject(cacheObject)
            }
        }
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/cache/usage")
    fun getCacheUsage(@RequestParam repoId: String): CacheUsageInfo {
        val (actualSize, buckets) = storageService.getUsedSpace(repoId)
        val trackedSize = trackingService.getBucketAggregation().first {it.repoId == repoId}.totalSize
        return CacheUsageInfo(
            managedBuckets = buckets,
            actualSize = actualSize,
            trackedSize = trackedSize,
            discrepancy = abs(actualSize - trackedSize)
        )
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
