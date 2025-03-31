package org.edu_sharing.rendering.modules.av

import io.mockk.mockk
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.AfterEach

class AvFileHelperTest {
    private val storageService: StorageService = mockk()
    private val underTest = AvFileHelper(storageService)


    @AfterEach
    fun after() {
        underTest.close()
    }

    /*
    @Test
    fun testInitOutPutFileGeneratesCorrectOutputFile() {
        // Arrange
        val extension = "pdf"

        // Act
        underTest.initOutputTempFile(extension)

        // Assert
        val path = underTest.outputFile.path
        assert(path.substringAfter(".") == extension)
        assert(path.substringBefore(".").length == 36)
    }


    @Test
    fun testFetchOriginalFileGeneratesCorrectFileAndCopiesResultFromStorageService() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "nodeId",
            hash = "hash",
            type = "type",
            mimeType = "audio/wav",
            repoId = "repo123"
        )
        val inputStream = ByteArrayInputStream(ByteArray(1))
        inputStream.use {
            every { storageService.getObjectStream(cacheObject, true) } returns it

            // Act
            underTest.fetchOriginalTempFile(cacheObject)

            // Assert
            assert(underTest.originalFile.length().compareTo(1) == 0)
            verify(exactly = 1) { storageService.getObjectStream(cacheObject, true) }
            confirmVerified(storageService)
        }
    }

    @Test
    fun testUploadToCacheThrowsExceptionIfOutputFileIsNotInitialized() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "nodeId",
            hash = "hash",
            type = "type",
            mimeType = "audio/wav",
            repoId = "repo123"
        )

        // Act and assert
        assertThrows<Exception> { underTest.uploadToCache(cacheObject) }
    }


    @Test
    fun testUploadToCacheCallsServiceMethodWithCorrectParams() {
        //Arrange
        val cacheObject = CacheObject(
            nodeId = "nodeId",
            hash = "hash",
            type = "type",
            mimeType = "audio/wav",
            repoId = "repo123"
        )
        val metadata = mapOf("test" to "value")
        val testFile = File("src/test/resources/fixtures/testFileWith1")
        underTest.outputFile = File("myoutput")
        testFile.copyTo(underTest.outputFile, true)

        val inputStreamSlot = slot<InputStream>()
        justRun { storageService.putObject(cacheObject, capture(inputStreamSlot), metadata) }

        // Act
        underTest.uploadToCache(cacheObject, metadata)

        // Assert
        verify(exactly = 1) { storageService.putObject(cacheObject, any(), metadata) }
        assert(inputStreamSlot.captured.readAllBytes().size == 2)
    }

     */
}
