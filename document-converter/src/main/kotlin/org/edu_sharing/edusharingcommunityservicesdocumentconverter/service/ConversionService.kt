package org.edu_sharing.edusharingcommunityservicesdocumentconverter.service

import org.edu_sharing.edusharingcommunityservicesdocumentconverter.exception.FormatException
import org.apache.coyote.BadRequestException
import org.jodconverter.core.document.DefaultDocumentFormatRegistry
import org.jodconverter.core.document.DocumentFormat
import org.jodconverter.local.LocalConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import kotlin.io.path.name

@Service
class ConversionService (
    private val converter: LocalConverter
){
    @Value("\${app.supportedExtensions}")
    lateinit var supportedExtensions: List<String>

    fun convert(
        inputFile: MultipartFile,
        sourceFormat: DocumentFormat,
        targetFormat: DocumentFormat
    ): ByteArrayOutputStream {
        val outputStream = ByteArrayOutputStream()

        converter.convert(inputFile.inputStream)
            .`as`(sourceFormat)
            .to(outputStream)
            .`as`(targetFormat)
            .execute()

        return outputStream
    }

    fun getTargetFormat(targetExtension: String): DocumentFormat {
        return DefaultDocumentFormatRegistry.getFormatByExtension(targetExtension)
            ?: throw FormatException("Unknown target format: $targetExtension")
    }

    fun getSourceFormat(inputFileName: String): DocumentFormat {
        val sourceExtension = inputFileName.substringAfter(".")
        if (! supportedExtensions.contains(sourceExtension)) {
            throw FormatException("Source extension $sourceExtension is not supported.")
        }
        return DefaultDocumentFormatRegistry.getFormatByExtension(sourceExtension)
            ?: throw FormatException("Unknown source format: $sourceExtension")
    }

    fun getFileName(inputFile: MultipartFile): String {
        val originalName = inputFile.originalFilename ?: throw BadRequestException("File name must not be null.")
        return Paths.get(originalName).name
    }
}