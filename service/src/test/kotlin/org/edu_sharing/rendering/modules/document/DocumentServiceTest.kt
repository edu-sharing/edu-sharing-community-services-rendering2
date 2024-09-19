package org.edu_sharing.rendering.modules.document

/**
class DocumentServiceTest {

    private val storageService = mockk<StorageService>()
    private val mainJobCreationService = mockk<MainJobCreationService>()
    private val module = mockk<DocumentRenderModule>()
    private lateinit var service: DocumentService

    @BeforeEach
    fun setUp() {
        service = DocumentService(storageImplementation = storageService, mainJobCreationService = mainJobCreationService)
    }

    @Test
    fun testGetObjectLinksReturnsLinkIfAlreadyCached() {
        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "document", hash = "abc123")
        val objectLink = ObjectLink(link = "mylink")
        val lookupObject = cacheObject.copy()
        lookupObject.mimeType = "image/jpeg"

        every { module.getTargetMimetype() } returns "image/jpeg"
        every { storageService.getObjectLink(lookupObject) } returns objectLink

        // Act
        val result = service.getObjectLinks(cacheObject, module)

        // Assert
        assert(result == mutableListOf(objectLink))

        verifySequence {
            module.getTargetMimetype()
            storageService.getObjectLink(lookupObject)
        }

        confirmVerified(module, storageService)
    }

    @Test
    fun testGetObjectLinksReturnsEmptyListIfNotCached() {
        val cacheObject = CacheObject(nodeId = "123", type = "document", hash = "abc123")
        val lookupObject = cacheObject.copy()
        lookupObject.mimeType = "image/jpeg"

        every { module.getTargetMimetype() } returns "image/jpeg"
        every { storageService.getObjectLink(lookupObject) } throws ResourceNotFoundException("test")

        val result = service.getObjectLinks(cacheObject, module)
        assert(result == null)

        verifySequence {
            module.getTargetMimetype()
            storageService.getObjectLink(lookupObject)
        }

        confirmVerified(module, storageService)
    }

    @Test
    fun testRetrieveOrCreateJobReturnsExistingJobIdIfFound() {

        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "document", hash = "abc123")
        every { mainJobCreationService.getExistingJobId(cacheObject) } returns "job123"

        // Act
        val result = service.retrieveOrCreateJob(cacheObject, module)

        // Assert
        assert(result == "job123")

        verify (exactly = 1) { mainJobCreationService.getExistingJobId(cacheObject) }
        confirmVerified(mainJobCreationService)
    }

    @Test
    fun testRetrieveOrCreateJobCreatesNewJobIfNoExistingFound() {
        // Arrange
        val cacheObject = CacheObject(nodeId = "123", type = "document", hash = "abc123")
        every { mainJobCreationService.getExistingJobId(cacheObject) } returns null
        every {module.module()} returns RenderModules.DOCUMENT
        every {mainJobCreationService.createMainJob(cacheObject, RenderModules.DOCUMENT)} returns "job123"

        // Act
        val result = service.retrieveOrCreateJob(cacheObject, module)

        // Assert
        assert(result == "job123")

        verifySequence {
            mainJobCreationService.getExistingJobId(cacheObject)
            module.module()
            mainJobCreationService.createMainJob(cacheObject, RenderModules.DOCUMENT)
        }
    }
}
 */