package org.edu_sharing.rendering.core

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import org.edu_sharing.rendering.edusharingRepo.EduTrackingService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryPublicKeyService
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.security.NodeSessionContextRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64

/**
 * Reproduces the production incident: `RenderDataService.getRenderModule` is
 * `@PreAuthorize`-protected and denies access by throwing `AuthorizationDeniedException`. That
 * exception used to fall through to `ApiExceptionHandler.handleGenericException` (no dedicated
 * handler existed for it / its `AccessDeniedException` supertype), so callers received a 500
 * instead of a 403. This drives a real request through the full MockMvc dispatch — including the
 * actual `ApiExceptionHandler` `@ControllerAdvice` — to verify the response is now 403.
 */
@WebMvcTest(RenderController::class, excludeAutoConfiguration = [SecurityAutoConfiguration::class])
@TestPropertySource(properties = ["app.security.enabled=false"])
class RenderControllerAccessDeniedTest(@param:Autowired val mockMvc: MockMvc) {

    @MockkBean
    lateinit var service: RenderDataService

    @MockkBean
    lateinit var trackingService: EduTrackingService

    @MockkBean
    lateinit var repositoryPublicKeyService: RepositoryPublicKeyService

    @MockkBean
    lateinit var nodeSessionContextRepository: NodeSessionContextRepository

    @MockkBean
    lateinit var moduleRegistry: ModuleRegistry

    private fun requestBody(): String {
        val node = """{"ref": {"id": "TEST_file.h5p", "repo": "repo-1"}, "mediatype": "file"}"""
        val payload = Base64.getEncoder().encodeToString(node.toByteArray())
        val signature = Base64.getEncoder().encodeToString("sig".toByteArray())
        return """
            {
              "nodeId": "TEST_file.h5p",
              "repoId": "repo-1",
              "securedNode": "$payload",
              "signature": "$signature",
              "signatureAlgorithm": "SHA256withRSA"
            }
        """.trimIndent()
    }

    @Test
    fun `returns 403 instead of 500 when the PreAuthorize check denies access`() {
        every { moduleRegistry.isFrontendRemoteRepository(any()) } returns false
        every { nodeSessionContextRepository.saveNode(any()) } just runs
        every { trackingService.trackObject(any(), any(), any()) } just runs
        every { service.getRenderModule(any(), any()) } throws AuthorizationDeniedException("Access Denied")

        mockMvc.perform(
            post("/public/renderdata")
                .content(requestBody())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
    }
}
