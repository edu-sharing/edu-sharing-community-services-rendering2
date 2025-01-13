package org.edu_sharing.rendering.edusharingRepo.services

import org.edu_sharing.generated.repository.backend.services.rest.client.ApiClient
import org.edu_sharing.generated.repository.backend.services.rest.client.api.AdminV1Api
import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.core.exception.ModuleNotRegisteredException
import org.edu_sharing.rendering.edusharingRepo.api.ApiClientFixes
import org.edu_sharing.rendering.edusharingRepo.dto.ActivateOptionalModuleRequest
import org.edu_sharing.rendering.edusharingRepo.dto.DeactivateOptionalModuleRequest
import org.edu_sharing.rendering.edusharingRepo.dto.RegisterRepositoryRequest
import org.edu_sharing.rendering.edusharingRepo.dto.RemoveRepositoryRequest
import org.edu_sharing.rendering.edusharingRepo.entity.ModuleSettings
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.security.CorsService
import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.utils.cleanUrl
import org.edu_sharing.rendering.utils.combinePath
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.scheduler.Schedulers
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*

@Service
class RepositoryRegistrationService(
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    private val storageService: StorageService,
    private val appInfo: AppInfo,
    private val corsService: CorsService,
    private val moduleRegistry: ModuleRegistry
) : RepositoryPublicKeyService {

    init {
        val registrations = repositoryRegistrationStorageService.getRegistrations()
        corsService.addOrigin(
            registrations
                .filter { it.domains != null }
                .flatMap { it.domains!! })
    }

    fun getWebClientByRepoId(repoId: String): WebClient {
        return getWebClient(
            repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
                .map { it.url }
                .orElseThrow { IllegalArgumentException("Repository not found for id: $repoId") }
        )
    }

    private fun getWebClient(url: String): WebClient {
        return WebClient
            .builder()
            .baseUrl(url)
            .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024 * 1024) }
            .build()
    }

    private fun createRegistration(url: String, force: Boolean): RepositoryRegistration {
        val metadata = getWebClient(url)
            .get()
            .uri {
                it.path("/metadata")
                    .queryParam("format", "lms")
                    .queryParam("external", true)
                    .build()
            }.accept(MediaType.APPLICATION_XML)
            .retrieve()
            .bodyToMono(String::class.java)
            .publishOn(Schedulers.boundedElastic())
            .mapNotNull {
                val buffer = it.byteInputStream()
                val props = Properties()
                props.loadFromXML(buffer)
                object {
                    val appId = props["appid"].toString()
                    val publicKey = props["public_key"].toString()
                    val domain = listOf("${props["clientprotocol"]}://${props["domain"]}:${props["clientport"]}".cleanUrl()) // todo we need to get all domains from the repository
                }
            }
            .block()


        if (metadata == null) {
            throw InvalidKeyException("Received metadata info is null")
        }

        if (!storageService.isStoringByRepoId() && repositoryRegistrationStorageService.getRegistrationCount() > 0) {
            if (force) {
                repositoryRegistrationStorageService.clearRegistrations()
                corsService.clearExternalOrigins()
            } else {
                throw IllegalArgumentException("It's not allowed to register more than one repository")
            }
        }


        val registration = RepositoryRegistration(
            repoId = metadata.appId,
            url = url,
            publicKey = metadata.publicKey,
            domains = metadata.domain,
            optionalModules = mutableListOf()
        )

        if (force) {
           repositoryRegistrationStorageService.getRegistrationByRepoId(metadata.appId)
               .ifPresent {
                   registration.id = it.id
               }
        }

        val storeRegistration = repositoryRegistrationStorageService.storeRegistration(registration)
        corsService.addOrigin(storeRegistration.domains ?: emptyList())
        return storeRegistration
    }


    @Transactional
    @CachePut("repositoryKeys", key = "#result.id")
    fun registerWithRepository(request: RegisterRepositoryRequest, force: Boolean = false): RepositoryRegistration {
        val registrationEntity = createRegistration(request.url, force)

        val adminV1Api = getAdminV1Api(request.url, request.username, request.password)
        adminV1Api.addApplication1(appInfo.internal.url.combinePath("public/metadata"))

        return registrationEntity
    }

    private fun getAdminV1Api(url: String, username: String, password: String): AdminV1Api {
        val apiClient: ApiClient = ApiClientFixes()
        apiClient.setBasePath("${url}/rest")
        apiClient.setUsername(username)
        apiClient.setPassword(password)
        val adminV1Api = AdminV1Api(apiClient)
        return adminV1Api
    }

    @Transactional
    @CacheEvict("repositoryKeys", key = "#request.repoId")
    fun deleteRepository(request: RemoveRepositoryRequest) : RepositoryRegistration {
        val entry = repositoryRegistrationStorageService.removeRegistration(request.repoId)
            .orElseThrow { IllegalArgumentException("Repository not found for id: ${request.repoId}") }

        val adminV1Api = getAdminV1Api(entry.url, request.username, request.password)
        adminV1Api.removeApplication(appInfo.appId)
        corsService.removeOrigin(entry.domains ?: emptyList())
        return entry
    }

    @Cacheable("repositoryKeys", key = "#repoId")
    override fun getRepositoryKey(repoId: String): PublicKey {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Repository not found for id: $repoId") }

        val publicKey = registration.publicKey
        val publicKeyData = publicKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")

        val keySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyData))
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)

    }

    fun getRegisteredRepositories(): List<RepositoryRegistration> {
        return repositoryRegistrationStorageService.getRegistrations()
    }

    fun activateOptionalModule(request: ActivateOptionalModuleRequest) {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(request.repoId)
            .orElseThrow { IllegalArgumentException("Repository not found for id: ${request.repoId}") }

        try {
            val renderModule = moduleRegistry.getRenderModule<RenderModule>(request.module)
            if (! renderModule.isOptionalModule()) {
                throw IllegalArgumentException("${request.module} is not an optional module")
            }
            if (renderModule is ThirdPartyModule) {
                renderModule.validateThirdPartyCredentials(request.credentials ?: emptyMap(), request.repoId)
                registration.module[request.module] = ModuleSettings(credentials = request.credentials ?: emptyMap())
            }
        } catch (_: ModuleNotRegisteredException) {
            throw IllegalArgumentException("${request.module} is not a valid module")
        }

        registration.optionalModules = registration.optionalModules.union(listOf(request.module)).toMutableList()
        repositoryRegistrationStorageService.storeRegistration(registration)
    }

    fun deactivateOptionalModule(request: DeactivateOptionalModuleRequest) {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(request.repoId)
            .orElseThrow { IllegalArgumentException("Repository not found for id: ${request.repoId}") }
        registration.optionalModules.removeAll(request.modules)
        repositoryRegistrationStorageService.storeRegistration(registration)
    }
}
