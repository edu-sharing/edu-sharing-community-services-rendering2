package org.edu_sharing.rendering.edusharingRepo.services

import io.mockk.mockk
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.security.CorsService
import org.edu_sharing.rendering.storage.StorageService
import org.springframework.web.reactive.function.client.WebClient

class RepositoryRegistrationServiceTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var eduSharingWebClient: WebClient
    private lateinit var underTest: RepositoryRegistrationService

    private val registrations = listOf<RepositoryRegistration>(
        RepositoryRegistration(
            id = "1",
            repoId = "repo1",
            url = "http://repo1.url",
            publicKey = "key1"
        ),
        RepositoryRegistration(
            id = "2",
            repoId = "repo2",
            url = "http://repo2.url",
            publicKey = "key2"
        )
    )

    private val repositoryRegistrationStorageService = mockk<RepositoryRegistrationStorageService>()
    private val storageService = mockk<StorageService>()
    private val appInfo = mockk<AppInfo>()
    private val corsService = mockk<CorsService>()
    private val moduleRegistry = mockk<ModuleRegistry>()

    /*@BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        eduSharingWebClient = WebClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .build()

        every { repositoryRegistrationStorageService.getRegistrations() } returns registrations
        justRun { corsService.addOrigin(any<List<String>>()) }

        underTest = RepositoryRegistrationService(
            repositoryRegistrationStorageService = repositoryRegistrationStorageService,
            storageService = storageService,
            appInfo = appInfo,
            corsService = corsService,
            moduleRegistry = moduleRegistry
        )
    }

    @AfterEach
    fun teardown() {
        mockServer.shutdown()
    }

    @Test
    fun testRegisterWithRepositoryCallsApiWithCorrectParamsAndStoresKey() {
        // Arrange
        val serverResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\"><properties><entry key=\"public_key\">testPublicKey</entry></properties>"
        mockServer.enqueue(MockResponse()
            .setBody(serverResponse)
            .setHeader("Content-Type", "application/xml")
            .setResponseCode(200))

        every { adminApi.addApplication1("http://localhost:1234/public/metadata") } returns ""
        justRun { privatePublicKeyService.storeRepositoryKey("testPublicKey") }

        // Act
        assertDoesNotThrow { service.registerWithRepository(body) }

        // Assert
        val request = mockServer.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/metadata?format=lms&external=true", request.path)

        verifySequence {
            adminApi.addApplication1("http://localhost:1234/public/metadata")
            privatePublicKeyService.storeRepositoryKey("testPublicKey")
        }
        confirmVerified(adminApi,privatePublicKeyService)

    }
    @Test
    fun testCreateRegistrationThrowsExceptionOnEmptyReturn() {
        // Arrange
        mockServer.enqueue(MockResponse()
            .setHeader("Content-Type", "application/xml")
            .setResponseCode(200))

        // Act and assert
        assertThrows<InvalidKeyException> { service.updatePublicRepositoryKey() }
    }

    @Test
    fun testCreateRegistrationThrowsExceptionOnReturnWithMissingEntry() {
        // Arrange
        val serverResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\"><properties><entry key=\"some_other\">testPublicKey</entry></properties>"
        mockServer.enqueue(MockResponse()
            .setHeader("Content-Type", "application/xml")
            .setBody(serverResponse)
            .setResponseCode(200))

        // Act and assert
        assertThrows<InvalidKeyException> { service.updatePublicRepositoryKey() }
    }
    */
}