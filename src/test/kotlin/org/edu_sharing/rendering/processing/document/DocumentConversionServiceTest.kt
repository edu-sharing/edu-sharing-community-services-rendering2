package org.edu_sharing.rendering.processing.document

import com.jayway.jsonpath.internal.path.PathCompiler.fail
import io.mockk.*
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.exception.UnknownSourceFormatException
import org.edu_sharing.rendering.modules.document.DocumentRenderModule
import org.edu_sharing.rendering.service.ContentTransferService
import org.edu_sharing.rendering.service.RenderModuleMappingService
import org.jodconverter.core.document.DefaultDocumentFormatRegistry
import org.jodconverter.core.job.ConversionJob
import org.jodconverter.core.job.ConversionJobWithOptionalSourceFormatUnspecified
import org.jodconverter.core.job.ConversionJobWithRequiredTargetFormatUnspecified
import org.jodconverter.local.LocalConverter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DocumentConversionServiceTest {
    private val converter: LocalConverter = mockk()
    private val contentTransferService: ContentTransferService = mockk()
    private val storageService: StorageService = mockk()
    private val underTest: DocumentConversionService = DocumentConversionService(
        converter = converter,
        contentTransferService = contentTransferService,
        storageImplementation = storageService
    )
    private val documentRenderModule: DocumentRenderModule = mockk()
    private val conJob: ConversionJobWithOptionalSourceFormatUnspecified = mockk()
    private val conJob2: ConversionJobWithOptionalSourceFormatUnspecified = mockk()
    private val conJob3: ConversionJobWithRequiredTargetFormatUnspecified = mockk()
    private val conJob4: ConversionJob = mockk()

    @Test
    fun testConvertAndMoveToCacheThrowsExceptionOnInvalidTargetMimeType() {
        // Arrange
        val cacheObject = getCacheObject()
        every { documentRenderModule.getTargetMimetype() } returns "someFluffyNonsense"
        every { documentRenderModule.module() } returns RenderModules.DOCUMENT

        // Act
        assertThrows<UnknownSourceFormatException> {
            underTest.convertAndMoveToCache(cacheObject, documentRenderModule)
        }
    }

    @Test
    fun testConvertAndMoveToCacheThrowsExceptionOnInvalidSourceMimeType() {
        // Arrange
        val cacheObject = getCacheObject(mimeType = "someFluffyNonsense")
        every { documentRenderModule.getTargetMimetype() } returns RenderModuleMappingService.ODT

        // Act
        assertThrows<UnknownSourceFormatException> {
            underTest.convertAndMoveToCache(cacheObject, documentRenderModule)
        }

        // Assert
        verify(exactly = 1) { documentRenderModule.getTargetMimetype() }
        confirmVerified(documentRenderModule)
    }

    @Test
    fun testConvertAndMoveToCacheCallsConverterWithCorrectArguments() {
        val cacheObject = getCacheObject()
        val inputStream = ByteArrayInputStream(ByteArray(0))
        inputStream.use {
            try {
                val expectedSourceFormat =
                    DefaultDocumentFormatRegistry.getFormatByMediaType(RenderModuleMappingService.ODT) ?:
                    throw Exception()
                val expectedTargetFormat =
                    DefaultDocumentFormatRegistry.getFormatByMediaType(MediaType.APPLICATION_PDF_VALUE) ?:
                    throw Exception()
                every { documentRenderModule.getTargetMimetype() } returns MediaType.APPLICATION_PDF_VALUE
                every { contentTransferService.getAsInputStream(cacheObject) } returns inputStream
                every { converter.convert(inputStream)} returns conJob
                every { conJob.`as`(expectedSourceFormat) } returns conJob2
                every { conJob2.to(any() as ByteArrayOutputStream) } returns conJob3
                every { conJob3.`as`(expectedTargetFormat) } returns conJob4
                justRun { conJob4.execute() }
                justRun { storageService.putObject(cacheObject, any()) }

                // Act
                underTest.convertAndMoveToCache(cacheObject, documentRenderModule)

                // Assert
                assert(cacheObject.size.compareTo(0) == 0)
                assert(cacheObject.mimeType == MediaType.APPLICATION_PDF_VALUE)

                verify(exactly = 2) { documentRenderModule.getTargetMimetype() }
                verify(exactly = 1) { contentTransferService.getAsInputStream(cacheObject) }
                verify(exactly = 1) { storageService.putObject(cacheObject, any()) }
                verify(exactly = 1) { converter.convert(inputStream)}
                verify(exactly = 1) { conJob.`as`(expectedSourceFormat) }
                verify(exactly = 1) { conJob2.to(any() as ByteArrayOutputStream) }
                verify(exactly = 1) { conJob3.`as`(expectedTargetFormat) }
                verify(exactly = 1) { conJob4.execute() }

                confirmVerified(
                    documentRenderModule,
                    contentTransferService,
                    storageService,
                    converter,
                    conJob,
                    conJob2,
                    conJob3,
                    conJob4
                )
            } catch (exception: Exception) {
                fail("Should not throw exception. $exception")
                return
            }
        }
    }

    private fun getCacheObject(mimeType: String = RenderModuleMappingService.ODT): CacheObject {
        return CacheObject(
            nodeId = "nodeId",
            hash = "hash",
            type = "file-odt",
            mimeType = mimeType
        )
    }

}