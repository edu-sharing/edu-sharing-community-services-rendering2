package org.edu_sharing.rendering.modules.av

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.InputStream

class AvFileHelperTest {
    private val storageService: StorageService = mockk()
    private val underTest = AvFileHelper(storageService)

    private val cacheObject = CacheObject(
        nodeId = "nodeId",
        hash = "hash",
        type = "type",
        mimeType = "audio/wav",
        repoId = "repo123"
    )

    @AfterEach
    fun after() {
        underTest.close()
    }

    @Test
    fun testUploadToCacheThrowsExceptionIfOutputFileIsNotInitialized() {
        assertThrows<Exception> { underTest.uploadToCache(cacheObject) }
    }

    @Test
    fun testUploadToCacheStreamsOutputFileToStorage() {
        // Arrange
        val metadata = mapOf("test" to "value")
        val expectedContent = "converted-av-data"
        underTest.initOutputTempFile("mp4")
        underTest.outputFile.writeText(expectedContent)

        var uploadedBytes: ByteArray? = null
        // the stream is consumed and closed inside uploadToCache, so read it in the answer
        every { storageService.putObject(cacheObject, any<InputStream>(), metadata) } answers {
            uploadedBytes = secondArg<InputStream>().readAllBytes()
        }

        // Act
        underTest.uploadToCache(cacheObject, metadata)

        // Assert
        assert(uploadedBytes!!.toString(Charsets.UTF_8) == expectedContent)
        verify(exactly = 1) { storageService.putObject(cacheObject, any<InputStream>(), metadata) }
    }
}
