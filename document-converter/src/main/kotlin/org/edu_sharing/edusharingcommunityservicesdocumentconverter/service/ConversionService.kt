package org.edu_sharing.edusharingcommunityservicesdocumentconverter.service

import org.apache.commons.io.FilenameUtils
import org.apache.coyote.BadRequestException
import org.edu_sharing.edusharingcommunityservicesdocumentconverter.exception.FormatException
import org.jodconverter.core.document.DefaultDocumentFormatRegistry
import org.jodconverter.core.document.DocumentFormat
import org.jodconverter.local.LocalConverter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import kotlin.io.path.name

@Service
class ConversionService (
    private val converter: LocalConverter
){
    @Value($$"${app.supportedExtensions}")
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

        if (targetFormat.name.lowercase() == "html") {
            return getStyledHtml(outputStream)
        }

        return outputStream
    }

    fun getTargetFormat(targetExtension: String): DocumentFormat {
        return DefaultDocumentFormatRegistry.getFormatByExtension(targetExtension)
            ?: throw FormatException("Unknown target format: $targetExtension")
    }

    fun getSourceFormat(inputFileName: String): DocumentFormat {
        val sourceExtension = FilenameUtils.getExtension(inputFileName)
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

    private fun getStyledHtml(outputStream: ByteArrayOutputStream): ByteArrayOutputStream {
        val htmlString = outputStream.toString(StandardCharsets.UTF_8.name())
        val document: Document = Jsoup.parse(htmlString)
        val styleTag = document.head().appendElement("style")
        styleTag.attr("type", "text/css")
        styleTag.attr("data-added-by", "Edu-Sharing Document Converter Service")
        styleTag.append("""
            table { border-collapse: collapse; }
            td { border-right: dotted 1px lightslategrey; border-left: dotted 1px lightslategrey; padding: .5em; }
            td:hover { background-color: lightsalmon; }
            tr { border: none; }
            tr:nth-child(odd) { background-color: lightsteelblue; }
        """.trimIndent())
        val modifiedOutputStream = ByteArrayOutputStream()
        modifiedOutputStream.write(document.html().toByteArray(StandardCharsets.UTF_8))

        return modifiedOutputStream
    }
}
