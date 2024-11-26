package org.edu_sharing.rendering.edusharingRepo.services

/**
class RepositoryRegistrationServiceTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var eduSharingWebClient: WebClient
    private var adminApi = mockk<AdminV1Api>()
    private var privatePublicKeyService = mockk<PrivatePublicKeyService>()
    private lateinit var service: RepositoryRegistrationService

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        eduSharingWebClient = WebClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .build()

        service = RepositoryRegistrationService(
            privatePublicKeyService = privatePublicKeyService,
            adminV1Api = adminApi,
            eduSharingWebClient = eduSharingWebClient,
            publicUrl = "http://localhost",
            port = "1234"
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
}
*/