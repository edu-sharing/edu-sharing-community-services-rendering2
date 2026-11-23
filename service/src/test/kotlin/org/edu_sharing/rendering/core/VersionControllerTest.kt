package org.edu_sharing.rendering.core

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import java.util.Properties

@WebMvcTest(VersionController::class, excludeAutoConfiguration = [SecurityAutoConfiguration::class])
@Import(VersionControllerTest.BuildInfo::class, RepositorySignatureVerifier::class)
class VersionControllerTest(@param:Autowired val mockMvc: MockMvc) {

    class BuildInfo {
        @Bean
        fun buildProperties() = BuildProperties(Properties().apply {
            setProperty("version", "11.0.4")
            setProperty("git.commit", "c0ffee")
            // unresolved maven placeholder, must be sent as null
            setProperty("docker.tag", "\${docker.tag}")
        })
    }

    @MockkBean
    lateinit var repositoryPublicKeyService: RepositoryPublicKeyService

    private val repoKeys: KeyPair = generateKeyPair()

    @BeforeEach
    fun setUp() {
        every { repositoryPublicKeyService.getRepositoryKey("local") } returns repoKeys.public
        every { repositoryPublicKeyService.getRepositoryKey("unknown") } throws IllegalArgumentException("Repository not found for id: unknown")
    }

    @Test
    fun `returns the version for a request signed by the registered repository`() {
        mockMvc.perform(signed("local", repoKeys.private))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value("11.0.4"))
            .andExpect(jsonPath("$.commit").value("c0ffee"))
            .andExpect(jsonPath("$.imageTag").doesNotExist())
    }

    @Test
    fun `rejects a request without signature`() {
        mockMvc.perform(get("/public/version")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `rejects a signature made with another key`() {
        mockMvc.perform(signed("local", generateKeyPair().private)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `rejects an unknown repository`() {
        mockMvc.perform(signed("unknown", repoKeys.private)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `rejects an old timestamp`() {
        mockMvc.perform(signed("local", repoKeys.private, ts = System.currentTimeMillis() - 60_000))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `rejects signed data that does not match app id and timestamp`() {
        mockMvc.perform(signed("local", repoKeys.private, signedData = "other"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `rejects an algorithm that is not whitelisted`() {
        mockMvc.perform(signed("local", repoKeys.private, algorithm = "SHA1withRSA"))
            .andExpect(status().isUnauthorized)
    }

    private fun signed(
        appId: String,
        key: PrivateKey,
        ts: Long = System.currentTimeMillis(),
        algorithm: String = "SHA512withRSA",
        signedData: String? = null,
    ): MockHttpServletRequestBuilder {
        val data = signedData ?: (appId + ts)
        val signature = Signature.getInstance(algorithm).run {
            initSign(key)
            update(data.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(sign())
        }
        return get("/public/version")
            .header("X-Edu-App-Id", appId)
            .header("X-Edu-App-Ts", ts.toString())
            .header("X-Edu-App-Signed", data)
            .header("X-Edu-App-Sig", signature)
            .header("X-Edu-App-SignedAlg", algorithm)
    }

    private fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
}
