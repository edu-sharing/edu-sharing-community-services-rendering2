package org.edu_sharing.edusharingcommunityservicesdocumentconverter.service

import org.apache.commons.io.FilenameUtils
import org.apache.coyote.BadRequestException
import org.edu_sharing.edusharingcommunityservicesdocumentconverter.config.ConverterProperties
import org.edu_sharing.edusharingcommunityservicesdocumentconverter.exception.FormatException
import org.edu_sharing.edusharingcommunityservicesdocumentconverter.exception.ResultTooLargeException
import org.edu_sharing.edusharingcommunityservicesdocumentconverter.io.TempFiles
import org.jodconverter.core.document.DefaultDocumentFormatRegistry
import org.jodconverter.core.document.DocumentFormat
import org.jodconverter.local.LocalConverter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import kotlin.io.path.name

@Service
class ConversionService (
    private val converter: LocalConverter,
    private val tempFiles: TempFiles,
    private val properties: ConverterProperties
){
    private val log = LoggerFactory.getLogger(ConversionService::class.java)

    @Value($$"${app.supportedExtensions}")
    lateinit var supportedExtensions: List<String>

    @Value($$"${app.normalizeTextDocuments}")
    var normalizeTextDocuments: Boolean = true

    /**
     * Converts the upload into a temp file and returns its path. The caller owns the file and must delete it
     * (the controller hands it to a delete-on-close resource). Nothing is buffered in memory: the upload is
     * moved to a temp file, the optional normalization pass writes a second one, jodconverter writes the result
     * to another, and HTML post-processing streams to a last one.
     */
    fun convert(
        inputFile: MultipartFile,
        sourceFormat: DocumentFormat,
        targetFormat: DocumentFormat
    ): Path {
        val input = tempFiles.create(".${sourceFormat.extension}")
        var normalized: Path? = null
        var output: Path? = null
        try {
            inputFile.transferTo(input)
            var conversionInput = input
            var inputFormat = sourceFormat
            if (normalizeTextDocuments && needsNormalization(sourceFormat, targetFormat)) {
                normalized = normalize(input, sourceFormat)
                conversionInput = normalized
                inputFormat = NORMALIZED_FORMAT
            }

            output = tempFiles.create(".${targetFormat.extension}")
            converter.convert(conversionInput.toFile())
                .`as`(inputFormat)
                .to(output.toFile())
                .`as`(targetFormat)
                .execute()

            if (targetFormat.name.lowercase() == "html") {
                val styled = addHtmlStyle(output)
                if (styled != output) {
                    tempFiles.deleteQuietly(output)
                    output = styled
                }
            }
            return output
        } catch (e: Throwable) {
            tempFiles.deleteQuietly(output)
            throw e
        } finally {
            tempFiles.deleteQuietly(input)
            tempFiles.deleteQuietly(normalized)
        }
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

    /** Writes [input] out through the [NORMALIZED_FORMAT] filter into a new temp file, which the caller deletes. */
    private fun normalize(input: Path, sourceFormat: DocumentFormat): Path {
        log.debug("Normalizing {} source through the {} filter before conversion",
            sourceFormat.extension, NORMALIZED_FORMAT.extension)
        val normalized = tempFiles.create(".${NORMALIZED_FORMAT.extension}")
        try {
            converter.convert(input.toFile())
                .`as`(sourceFormat)
                .to(normalized.toFile())
                .`as`(NORMALIZED_FORMAT)
                .execute()
            return normalized
        } catch (e: Throwable) {
            tempFiles.deleteQuietly(normalized)
            throw e
        }
    }

    /**
     * Injects the table CSS before `</head>`. LibreOffice writes the head at the very beginning, so a bounded scan
     * window is enough and the rest of the file is streamed through unchanged. Only if no `</head>` is found the
     * (memory hungry) Jsoup DOM is used, and only up to `maxHtmlDomBytes`.
     */
    internal fun addHtmlStyle(html: Path): Path {
        val styled = tempFiles.create(".html")
        try {
            val window = properties.htmlStyleScanWindow.toBytes().toInt()
            Files.newInputStream(html).use { input ->
                val prefix = input.readNBytes(window)
                // ISO-8859-1 maps bytes 1:1, so the byte offset of the ASCII marker is preserved.
                val index = String(prefix, StandardCharsets.ISO_8859_1).lowercase().indexOf("</head>")
                if (index >= 0) {
                    Files.newOutputStream(styled).use { out ->
                        out.write(prefix, 0, index)
                        out.write(STYLE_TAG.toByteArray(StandardCharsets.UTF_8))
                        out.write(prefix, index, prefix.size - index)
                        input.transferTo(out)
                    }
                    return styled
                }
            }
            val size = Files.size(html)
            if (size > properties.maxHtmlDomBytes.toBytes()) {
                throw ResultTooLargeException(
                    "Converted HTML is ${size / (1024 * 1024)} MB and has no <head> section; styling it would " +
                        "exceed the limit of ${properties.maxHtmlDomBytes.toMegabytes()} MB."
                )
            }
            val document: Document = Jsoup.parse(html.toFile(), StandardCharsets.UTF_8.name())
            document.head().appendElement("style")
                .attr("type", "text/css")
                .attr("data-added-by", STYLE_MARKER)
                .append(STYLE_CSS)
            Files.writeString(styled, document.html(), StandardCharsets.UTF_8)
            return styled
        } catch (e: Throwable) {
            tempFiles.deleteQuietly(styled)
            throw e
        }
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

        private const val STYLE_MARKER = "Edu-Sharing Document Converter Service"
        private val STYLE_CSS = """
            table { border-collapse: collapse; }
            td { border-right: dotted 1px lightslategrey; border-left: dotted 1px lightslategrey; padding: .5em; }
            td:hover { background-color: lightsalmon; }
            tr { border: none; }
            tr:nth-child(odd) { background-color: lightsteelblue; }
        """.trimIndent()
        private val STYLE_TAG =
            "<style type=\"text/css\" data-added-by=\"$STYLE_MARKER\">\n$STYLE_CSS\n</style>\n"
    }
}
