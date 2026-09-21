package org.edu_sharing.rendering.edusharingRepo.services

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.edusharingRepo.RestClientProvider
import org.edu_sharing.rendering.edusharingRepo.dto.ActivateOptionalModuleRequest
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.moodle.MoodleRenderModule
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.reactive.function.client.WebClient
import java.util.Optional

/**
 * Optional credentials must be truly optional: compose/Helm always render every key the template
 * lists, so an unset env var reaches the service as an empty string. [RepositoryRegistrationService]
 * normalizes that to "absent" when a module is activated.
 */
@ExtendWith(MockKExtension::class)
class RepositoryRegistrationServiceCredentialsTest {

    private val repoId = "repo1"

    private val repositoryRegistrationStorageService = mockk<RepositoryRegistrationStorageService>()
    private val moduleRegistry = mockk<ModuleRegistry>()
    private val renderModule = mockk<MoodleRenderModule>()

    private lateinit var registration: RepositoryRegistration
    private lateinit var underTest: RepositoryRegistrationService

    @BeforeEach
    fun setup() {
        registration = RepositoryRegistration(
            repoId = repoId,
            url = "http://repo.url",
            publicKey = "key"
        )

        every { repositoryRegistrationStorageService.getRegistrationByRepoId(repoId) } returns Optional.of(registration)
        every { repositoryRegistrationStorageService.storeRegistration(any()) } answers { firstArg() }
        every { moduleRegistry.getRenderModule<RenderModule>("MOODLE") } returns renderModule
        every { renderModule.isOptionalModule() } returns true

        underTest = RepositoryRegistrationService(
            repositoryRegistrationStorageService = repositoryRegistrationStorageService,
            storageService = mockk<StorageService>(relaxed = true),
            appInfo = mockk<AppInfo>(relaxed = true),
            moduleRegistry = moduleRegistry,
            metadataService = mockk<MetadataService>(relaxed = true),
            restClientProvider = mockk<RestClientProvider>(relaxed = true),
            webClientBuilder = mockk<WebClient.Builder>(relaxed = true)
        )
    }

    @Test
    fun blankCredentialValuesAreNotStoredAndNotValidated() {
        val validated = slot<Map<String, String>>()
        justRun { renderModule.validateThirdPartyCredentials(capture(validated), repoId) }

        underTest.activateOptionalModule(
            ActivateOptionalModuleRequest(
                repoId = repoId,
                module = "MOODLE",
                credentials = mapOf(
                    "baseurl" to "http://moodle:8080",
                    "publicurl" to "",
                    "token" to "wstoken",
                    "user" to "   "
                )
            )
        )

        val expected = mapOf("baseurl" to "http://moodle:8080", "token" to "wstoken")
        assertEquals(expected, validated.captured)
        assertEquals(expected, registration.module["MOODLE"]?.credentials)
    }

    @Test
    fun configuredCredentialValuesArePassedThroughUnchanged() {
        val validated = slot<Map<String, String>>()
        justRun { renderModule.validateThirdPartyCredentials(capture(validated), repoId) }

        val credentials = mapOf(
            "baseurl" to "http://moodle:8080",
            "publicurl" to "https://moodle.example.com"
        )

        underTest.activateOptionalModule(
            ActivateOptionalModuleRequest(repoId = repoId, module = "MOODLE", credentials = credentials)
        )

        assertEquals(credentials, validated.captured)
        assertEquals(credentials, registration.module["MOODLE"]?.credentials)
    }

    /**
     * Closes the loop for the Moodle/SCORM display switch: an unset env var reaches the service as an
     * empty string, must not be stored, and so leaves the module defaulting to "on" in
     * [org.edu_sharing.rendering.modules.moodle.MoodleRenderModule.getAdditionalData].
     */
    @Test
    fun blankDisplaySwitchIsNotStored() {
        val validated = slot<Map<String, String>>()
        justRun { renderModule.validateThirdPartyCredentials(capture(validated), repoId) }

        underTest.activateOptionalModule(
            ActivateOptionalModuleRequest(
                repoId = repoId,
                module = "MOODLE",
                credentials = mapOf(
                    "baseurl" to "http://moodle:8080",
                    "showPreviewIframe" to ""
                )
            )
        )

        val expected = mapOf("baseurl" to "http://moodle:8080")
        assertEquals(expected, validated.captured)
        assertEquals(expected, registration.module["MOODLE"]?.credentials)
    }
}
