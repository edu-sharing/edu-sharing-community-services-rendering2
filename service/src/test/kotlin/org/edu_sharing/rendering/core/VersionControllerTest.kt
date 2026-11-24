package org.edu_sharing.rendering.core

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.edu_sharing.rendering.edusharingRepo.services.AppAssertionProperties
import org.edu_sharing.rendering.edusharingRepo.services.AppAssertionTestTokens
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryAppAssertionVerifier
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryPublicKeyService
import org.edu_sharing.rendering.edusharingRepo.services.RepositorySignatureVerifier
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Properties

@WebMvcTest(VersionController::class, excludeAutoConfiguration = [SecurityAutoConfiguration::class])
@Import(
    VersionControllerTest.BuildInfo::class,
    BuildInfoService::class,
    RepositorySignatureVerifier::class,
    RepositoryAppAssertionVerifier::class,
    AppAssertionProperties::class,
)
class VersionControllerTest(@param:Autowired val mockMvc: MockMvc) {

    class BuildInfo {
        @Bean
        fun buildProperties() = BuildProperties(Properties().apply {
            setProperty("version", "11.0.4")
            setProperty("time", "1795439109000")
            setProperty("git.commit", "c0ffee")
            // unresolved maven placeholder, must be sent as null
            setProperty("docker.tag", "\${docker.tag}")
        })
    }

    @MockkBean
    lateinit var repositoryPublicKeyService: RepositoryPublicKeyService

    private val tokens = AppAssertionTestTokens()

    @BeforeEach
    fun setUp() {
        every { repositoryPublicKeyService.getRepositoryKey("local") } returns tokens.keys.public
        every { repositoryPublicKeyService.getRepositoryKey("unknown") } throws IllegalArgumentException("Repository not found for id: unknown")
    }

    @Test
    fun `returns the version for a request with the assertion of the registered repository`() {
        // Arrange
        val assertion = tokens.create()

        // Act
        val result = mockMvc.perform(get("/public/version").header(RepositoryAppAssertionVerifier.HEADER, assertion))

        // Assert
        result.andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value("11.0.4"))
            .andExpect(jsonPath("$.commit").value("c0ffee"))
            .andExpect(jsonPath("$.imageTag").doesNotExist())
            .andExpect(jsonPath("$.buildTime").value("2026-11-23T13:05:09Z"))
    }

    @Test
    fun `rejects a request without assertion`() {
        // Arrange
        val request = get("/public/version")

        // Act
        val result = mockMvc.perform(request)

        // Assert
        result.andExpect(status().isUnauthorized)
    }

    @Test
    fun `rejects the assertion of an unknown repository`() {
        // Arrange
        val assertion = tokens.create(claims = mapOf("iss" to "unknown"))

        // Act
        val result = mockMvc.perform(get("/public/version").header(RepositoryAppAssertionVerifier.HEADER, assertion))

        // Assert
        result.andExpect(status().isUnauthorized)
    }
}
