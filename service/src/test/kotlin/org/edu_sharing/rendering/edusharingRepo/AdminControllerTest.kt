package org.edu_sharing.rendering.edusharingRepo

import com.ninjasquad.springmockk.MockkBean
import io.mockk.confirmVerified
import io.mockk.justRun
import io.mockk.verify
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.*
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

@WebMvcTest(
    AdminController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class],
    properties = ["edu_sharing.registration.enabled=true"]
)
class AdminControllerTest(@Autowired val mockMvc: MockMvc) {

    @MockkBean
    lateinit var service: RepositoryRegistrationService

    @Test
    fun testRegisterWithRepoFetchesRepoKeyViaService() {
        // Arrange
        justRun { service.registerWithRepository() }

        // Act
        mockMvc.perform(put("/admin/repository/register")).andExpect(status().isOk).andReturn()

        // Assert
        verify(exactly = 1) { service.registerWithRepository() }
        confirmVerified(service)
    }

    /**
    @Test
    fun testUpdatePublicRepositoryKeyCallsCorrectServiceMethod() {
        // Arrange
        justRun { service.updatePublicRepositoryKey() }

        // Act
        mockMvc.perform(patch("/admin/repository/publicKey")).andExpect(status().isOk)

        // Assert
        verify(exactly = 1) { service.updatePublicRepositoryKey() }
        confirmVerified(service)
    }

    @Test
    fun testUpdatePublicRepositoryKeyReturnsNotFoundOnInvalidKeyError() {
        // Arrange
        every { service.updatePublicRepositoryKey() } throws InvalidKeyException()

        // Act
        mockMvc.perform(patch("/admin/repository/publicKey")).andExpect(status().isNotFound)

        // Assert
        verify(exactly = 1) { service.updatePublicRepositoryKey() }
        confirmVerified(service)
    }
    **/
}