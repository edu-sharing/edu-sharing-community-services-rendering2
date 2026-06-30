package org.edu_sharing.rendering.core

import com.ninjasquad.springmockk.MockkBean
import org.edu_sharing.rendering.edusharingRepo.EduTrackingService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryPublicKeyService
import org.edu_sharing.rendering.security.NodeSessionContextRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64

@WebMvcTest(RenderController::class, excludeAutoConfiguration = [SecurityAutoConfiguration::class])
class RenderControllerTest(@param:Autowired val mockMvc: MockMvc) {

    @MockkBean
    lateinit var service: RenderDataService

    @MockkBean
    lateinit var trackingService: EduTrackingService

    @MockkBean
    lateinit var repositoryPublicKeyService: RepositoryPublicKeyService

    @MockkBean
    lateinit var nodeSessionContextRepository: NodeSessionContextRepository

    private fun requestBody(algorithm: String): String {
        val payload = Base64.getEncoder().encodeToString("node".toByteArray())
        val signature = Base64.getEncoder().encodeToString("sig".toByteArray())
        return """
            {
              "nodeId": "TEST_file.h5p",
              "repoId": "repo-1",
              "securedNode": "$payload",
              "signature": "$signature",
              "signatureAlgorithm": "$algorithm"
            }
        """.trimIndent()
    }

    @Test
    fun `rejects a signature algorithm that is not whitelisted`() {
        // MD5withRSA is not in the default whitelist (SHA256withRSA, SHA512withRSA);
        // the check runs before any signature verification, so no collaborators are touched.
        mockMvc.perform(
            post("/public/renderdata")
                .content(requestBody("MD5withRSA"))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isBadRequest)
    }
}
