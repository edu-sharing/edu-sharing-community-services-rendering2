package org.edu_sharing.rendering.edusharingRepo

/**
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
        justRun { service.registerWithRepository(body) }

        // Act
        mockMvc.perform(put("/admin/repository/register")).andExpect(status().isOk).andReturn()

        // Assert
        verify(exactly = 1) { service.registerWithRepository(body) }
        confirmVerified(service)
    }


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
        mockMvc.perform(patch("/admin/repository/publicKey")).andExpect(status().isNotFound)        // Assert
        verify(exactly = 1) { service.updatePublicRepositoryKey() }
        confirmVerified(service)
    }

}
 */