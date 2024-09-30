package org.edu_sharing.rendering.modules.eduHtml

/**
class EduHtmlConversionServiceTest {
    private val contentTransferService: ContentTransferService = mockk()
    private val storageService: StorageService = mockk()
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
                type = "eduhtml"
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
                    targetPath = "$nodeId/index.html",
                )
                storageService.putObject(
                    cacheObject = coCss,
                    inputStream = any(),
                    targetPath = "$nodeId/style.css",
                )
                storageService.putObject(
                    cacheObject = coJs,
                    inputStream = any(),
                    targetPath = "$nodeId/assets/index.js",
                )
                storageService.putObject(
                    cacheObject = coJpg,
                    inputStream = any(),
                    targetPath = "$nodeId/assets/vinni.jpg",
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
                type = "eduhtml"
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
                type = "eduhtml"
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
        */