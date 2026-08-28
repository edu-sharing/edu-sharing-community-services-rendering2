package org.edu_sharing.edusharingcommunityservicesdocumentconverter.service

import org.apache.commons.io.FilenameUtils
import org.apache.coyote.BadRequestException
import org.edu_sharing.edusharingcommunityservicesdocumentconverter.exception.FormatException
import org.jodconverter.core.document.DefaultDocumentFormatRegistry
import org.jodconverter.core.document.DocumentFormat
import org.jodconverter.local.LocalConverter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import kotlin.io.path.name

@Service
class ConversionService (
    private val converter: LocalConverter
){
    private val log = LoggerFactory.getLogger(ConversionService::class.java)

    @Value($$"${app.supportedExtensions}")
    lateinit var supportedExtensions: List<String>

    @Value($$"${app.normalizeTextDocuments}")
    var normalizeTextDocuments: Boolean = true

    fun convert(
        inputFile: MultipartFile,
        sourceFormat: DocumentFormat,
        targetFormat: DocumentFormat
    ): ByteArrayOutputStream {
        val outputStream = ByteArrayOutputStream()

        var input: InputStream = inputFile.inputStream
        var inputFormat = sourceFormat
        if (normalizeTextDocuments && needsNormalization(sourceFormat, targetFormat)) {
            input = ByteArrayInputStream(normalize(input, sourceFormat).toByteArray())
            inputFormat = NORMALIZED_FORMAT
        }

        converter.convert(input)
            .`as`(inputFormat)
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

    private fun normalize(input: InputStream, sourceFormat: DocumentFormat): ByteArrayOutputStream {
        log.debug("Normalizing {} source through the {} filter before conversion",
            sourceFormat.extension, NORMALIZED_FORMAT.extension)
        val normalized = ByteArrayOutputStream()
        converter.convert(input)
            .`as`(sourceFormat)
            .to(normalized)
            .`as`(NORMALIZED_FORMAT)
            .execute()

        return normalized
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

    internal companion object {
        /** Intermediate format of the normalization pass. */
        private val NORMALIZED_FORMAT: DocumentFormat = DefaultDocumentFormatRegistry.DOCX

        /** Word processing formats that can hold a table inside a floating text box. */
        private val FRAME_CAPABLE_EXTENSIONS = setOf("doc", "docx", "odt", "ott", "rtf")

        /**
         * LibreOffice silently drops the content and the borders of tables that sit inside a floating
         * text box when it converts a word processing document through the UNO API — the rows keep
         * their height but stay empty, so e.g. a docx whose whole appointment schedule lives in a
         * "Textfeld" comes out as a blank area. Writing the document out through LibreOffice's own
         * docx filter first rebuilds those shapes into a form its layout engine handles, so the second
         * pass renders the tables. Only word processing sources can carry such shapes, and a docx
         * target already goes through that filter, so those conversions are left as a single pass.
         */
        internal fun needsNormalization(sourceFormat: DocumentFormat, targetFormat: DocumentFormat) =
            sourceFormat.extension in FRAME_CAPABLE_EXTENSIONS &&
                    targetFormat.extension != NORMALIZED_FORMAT.extension
    }
}
