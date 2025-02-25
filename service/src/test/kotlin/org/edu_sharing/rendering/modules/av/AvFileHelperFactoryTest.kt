package org.edu_sharing.rendering.modules.av

import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AvFileHelperFactoryTest {
    private val storageService = mockk<StorageService>()

    private val underTest = AvFileHelperFactory(storageService)

/*    @Test
    fun testCreateAvFileHelperReturnsFileHelperWithStorageService() {
        // Arrange
        val result = underTest.createAvFileHelper()
        result.outputFile = File("src/test/resources/fixtures/testFileWith1")
        val cacheObject = mockk<CacheObject>()

        val inputStreamSlot = slot<InputStream>()
        justRun { storageService.putObject(cacheObject, capture(inputStreamSlot)) }

        // Act
        result.uploadToCache(cacheObject)

        // Assert
        val uploadedStream = inputStreamSlot.captured
        assert(uploadedStream.readAllBytes().toString(Charsets.UTF_8) == "1\n")
    }*/
}