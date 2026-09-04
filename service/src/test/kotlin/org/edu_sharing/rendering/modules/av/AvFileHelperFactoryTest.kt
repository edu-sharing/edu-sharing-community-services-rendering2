package org.edu_sharing.rendering.modules.av

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.io.InputStream

@ExtendWith(MockKExtension::class)
class AvFileHelperFactoryTest {
    private val storageService = mockk<StorageService>()

    private val underTest = AvFileHelperFactory(storageService)

    @Test
    fun testCreateAvFileHelperReturnsFileHelperWithStorageService() {
        // Arrange
        val result = underTest.createAvFileHelper()
        result.outputFile = File("src/test/resources/fixtures/testFileWith1")
        val cacheObject = mockk<CacheObject>(relaxed = true)

        var uploadedBytes: ByteArray? = null
        // the stream is consumed and closed inside uploadToCache, so read it in the answer
        every { storageService.putObject(cacheObject, any<() -> InputStream>()) } answers {
            uploadedBytes = secondArg<() -> InputStream>().invoke().readAllBytes()
        }

        // Act
        result.uploadToCache(cacheObject)

        // Assert
        assert(uploadedBytes!!.toString(Charsets.UTF_8) == "1\n")
    }
}
