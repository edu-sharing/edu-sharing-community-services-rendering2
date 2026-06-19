package org.edu_sharing.rendering.edusharingRepo.services

import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.core.exception.ModuleNotRegisteredException
import org.edu_sharing.rendering.edusharingRepo.RestClientProvider
import org.edu_sharing.rendering.edusharingRepo.dto.ActivateOptionalModuleRequest
import org.edu_sharing.rendering.edusharingRepo.dto.DeactivateOptionalModuleRequest
import org.edu_sharing.rendering.edusharingRepo.dto.RegisterRepositoryRequest
import org.edu_sharing.rendering.edusharingRepo.dto.RemoveRepositoryRequest
import org.edu_sharing.rendering.edusharingRepo.entity.ModuleSettings
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.utils.cleanUrl
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
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
    private val moduleRegistry: ModuleRegistry,
    private val metadataService: MetadataService,
    private val restClientProvider: RestClientProvider,
    private val webClientBuilder: WebClient.Builder
) : RepositoryPublicKeyService {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getWebClientByRepoId(repoId: String): WebClient {
        log.debug("Building WebClient for repoId: $repoId")
        return getWebClient(
            repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
                .map { it.url }
                .orElseThrow { IllegalArgumentException("Repository not found for id: $repoId") }
        )
    }

    private fun getWebClient(url: String): WebClient {
        return webClientBuilder
            .clone()
            .baseUrl(url)
            .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024 * 1024) }
            .build()
    }

    private fun createRegistration(request: RegisterRepositoryRequest, force: Boolean): RepositoryRegistration {
        log.debug("Fetching metadata from repository URL: ${request.url} (force=$force)")
        val metadata = getWebClient(request.url)
            .get()
            .uri {
                it.path("/metadata")
                    .queryParam("format", "lms")
                    .queryParam("external", true)
                    .build()
            }.accept(MediaType.APPLICATION_XML)
            .retrieve()
            .bodyToMono<String>()
            .publishOn(Schedulers.boundedElastic())
            .mapNotNull {
                val buffer = it.byteInputStream()
                val props = Properties()
                props.loadFromXML(buffer)
                object {
                    val appId = props["appid"].toString()
                    val publicKey = props["public_key"].toString()
                    val domain = listOf("${props["clientprotocol"]}://${props["domain"]}:${props["clientport"]}".cleanUrl())
                }
            }
            .block()


        if (metadata == null) {
            throw InvalidKeyException("Received metadata info is null")
        }

        log.debug("Received metadata from ${request.url}: appId=${metadata.appId}, domains=${metadata.domain}")

        if (!storageService.isStoringByRepoId() && repositoryRegistrationStorageService.getRegistrationCount() > 0) {
            if (force) {
                repositoryRegistrationStorageService.clearRegistrations()
            } else {
                throw IllegalArgumentException("It's not allowed to register more than one repository")
            }
        }

        val signatureAlgorithm = getSigningAlgorithm(request.url)
        log.debug("Signing algorithm for ${request.url}: $signatureAlgorithm")

        val registration = RepositoryRegistration(
            repoId = metadata.appId,
            url = request.url,
            publicKey = metadata.publicKey,
            domains = metadata.domain,
            optionalModules = mutableListOf(),
            quota = request.quota,
            buckets = request.externalBuckets,
            signingAlgorithm = signatureAlgorithm
        )

        if (force) {
           repositoryRegistrationStorageService.getRegistrationByRepoId(metadata.appId)
               .ifPresent {
                   registration.id = it.id
               }
        }

        return repositoryRegistrationStorageService.storeRegistration(registration)
    }

    @Transactional
    @CachePut("repositoryKeys", key = "#result.id")
    fun registerWithRepository(request: RegisterRepositoryRequest, force: Boolean = false, useInternal: Boolean = false): RepositoryRegistration {
        log.debug("Registering with repository at ${request.url} (force=$force, useInternal=$useInternal)")
        val registrationEntity = createRegistration(request, force)
        log.debug("Registration entity created for repoId=${registrationEntity.repoId}; pushing metadata file to repository")
        val adminV1Api = restClientProvider.getAdminV1Client(request.url, request.username, request.password)
        metadataService.generateMetadataFile(useInternal = useInternal).use {
            adminV1Api.addApplication(it.file)
        }
        registrationEntity.signingAlgorithm = getSigningAlgorithm(registrationEntity.url)
        return registrationEntity
    }

    @Transactional
    @CacheEvict("repositoryKeys", key = "#request.repoId")
    fun deleteRepository(request: RemoveRepositoryRequest) : RepositoryRegistration {
        log.debug("Deleting registration for repoId=${request.repoId}")
        val entry = repositoryRegistrationStorageService.removeRegistration(request.repoId)
            .orElseThrow { IllegalArgumentException("Repository not found for id: ${request.repoId}") }

        val adminV1Api = restClientProvider.getAdminV1Client(entry.url, request.username, request.password)
        adminV1Api.removeApplication(appInfo.appId)
        return entry
    }

    @Cacheable("repositoryKeys", key = "#repoId")
    override fun getRepositoryKey(repoId: String): PublicKey {
        log.debug("Cache miss for repository public key, loading from storage for repoId: $repoId")
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
        log.debug("Activating optional module '${request.module}' for repoId=${request.repoId}")
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

    fun setCspHeader(repoId: String, module: String, cspHeader: String?) {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Repository not found for id: $repoId") }
        val moduleSettings = registration.module[module] ?: ModuleSettings()
        moduleSettings.cspHeader = cspHeader
        registration.module[module] = moduleSettings
        repositoryRegistrationStorageService.storeRegistration(registration)
    }

    fun deactivateOptionalModule(request: DeactivateOptionalModuleRequest) {
        log.debug("Deactivating modules ${request.modules} for repoId=${request.repoId}")
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(request.repoId)
            .orElseThrow { IllegalArgumentException("Repository not found for id: ${request.repoId}") }
        registration.optionalModules.removeAll(request.modules)
        repositoryRegistrationStorageService.storeRegistration(registration)
    }

    private fun getSigningAlgorithm(url: String): String {
        log.debug("Fetching signing algorithm from repository about endpoint: $url")
        val aboutApiClient = restClientProvider.getAboutApiClient(url)
        val about = aboutApiClient.about()
        val algorithm = about.defaultSignatureAlgorithm ?: "SHA1withRSA"
        log.debug("Repository at $url reports signing algorithm: $algorithm")
        return algorithm
    }

}
