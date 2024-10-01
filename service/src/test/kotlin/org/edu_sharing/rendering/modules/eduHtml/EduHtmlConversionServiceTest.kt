package org.edu_sharing.rendering.modules.eduHtml

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlConversionService
import org.edu_sharing.rendering.storage.StaticStorageService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType
import java.io.File

class EduHtmlConversionServiceTest {
    private val contentTransferService: ContentTransferService = mockk()
    private val storageService: StaticStorageService = mockk()
    private val underTest = EduHtmlConversionService(contentTransferService, storageService)

    @Test
    fun testIfCacheDataUnzipsFileAndPutsContentInCorrectStorageFolders() {
        val file = File("src/test/resources/fixtures/testhtml.zip")
        val inputStream = file.readBytes().inputStream()
        val nodeId = "nodeId"
        inputStream.use {
            val cacheObject = CacheObject(
                nodeId = nodeId,
                hash = "hash",
                type = "eduhtml",
                repoId = "repo123"
            )

            val coHtml = cacheObject.copy(mimeType = MediaType.TEXT_HTML_VALUE)
            val coCss = cacheObject.copy(mimeType = "text/css")
            val coJpg = cacheObject.copy(mimeType = MediaType.IMAGE_JPEG_VALUE)
            val coJs = cacheObject.copy(mimeType = "text/javascript")

            every { contentTransferService.getAsInputStream(cacheObject) } returns inputStream

            justRun {
                storageService.putObject(
                    cacheObject = any(),
                    inputStream = any(),
                    targetPath = any(),
                    metadata = any()
                )
            }

            // Act
            underTest.cacheData(cacheObject)

            // Assert
            verify(exactly = 1) { contentTransferService.getAsInputStream(any()) }
            verifyAll {
                storageService.putObject(
                    cacheObject = coHtml,
                    inputStream = any(),
                    targetPath = "index.html",
                    metadata = any()
                )
                storageService.putObject(
                    cacheObject = coCss,
                    inputStream = any(),
                    targetPath = "style.css",
                    metadata = any()
                )
                storageService.putObject(
                    cacheObject = coJs,
                    inputStream = any(),
                    targetPath = "assets/index.js",
                    metadata = any()
                )
                storageService.putObject(
                    cacheObject = coJpg,
                    inputStream = any(),
                    targetPath = "assets/vinni.jpg",
                    metadata = any()
                )
            }
        }
        confirmVerified(storageService, contentTransferService)
    }


    @Test
    fun testIfCacheDataThrowsExceptionOnCompletelyEmptyZipFile() {
        // Arrange
        val file = File("src/test/resources/fixtures/emptyzip.zip")
        val inputStream = file.readBytes().inputStream()
        val nodeId = "nodeId"
        inputStream.use {
            val cacheObject = CacheObject(
                nodeId = nodeId,
                hash = "hash",
                type = "eduhtml",
                repoId = "repo123"
            )
            every { contentTransferService.getAsInputStream(cacheObject) } returns inputStream

            // Assert
            assertThrows<ConversionException> {
                // Act
                underTest.cacheData(cacheObject)
            }
            verify(exactly = 1) { contentTransferService.getAsInputStream(any()) }
            confirmVerified(contentTransferService)
        }
    }

    @Test
    fun testIfCacheDataThrowsExceptionOnZipFileContainingOnlyFolders() {
        // Arrange
        val file = File("src/test/resources/fixtures/ziponlyfolders.zip")
        val inputStream = file.readBytes().inputStream()
        val nodeId = "nodeId"
        inputStream.use {
            val cacheObject = CacheObject(
                nodeId = nodeId,
                hash = "hash",
                type = "eduhtml",
                repoId = "repo123"
            )
            every { contentTransferService.getAsInputStream(cacheObject) } returns inputStream

            // Assert
            assertThrows<ConversionException> {

                // Act
                underTest.cacheData(cacheObject)
            }

            verify(exactly = 1) { contentTransferService.getAsInputStream(any()) }
            confirmVerified(contentTransferService)
        }
    }
}
