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
    @Value($$"${app.supportedExtensions}")
    lateinit var supportedExtensions: List<String>

    /**
     * Converts the upload into a temp file and returns its path. The caller owns the file and must delete it
     * (the controller hands it to a delete-on-close resource). Nothing is buffered in memory: the upload is
     * moved to a temp file, jodconverter writes to a second one, and HTML post-processing streams to a third.
     */
    fun convert(
        inputFile: MultipartFile,
        sourceFormat: DocumentFormat,
        targetFormat: DocumentFormat
    ): Path {
        val input = tempFiles.create(".${sourceFormat.extension}")
        var output: Path? = null
        try {
            inputFile.transferTo(input)
            output = tempFiles.create(".${targetFormat.extension}")
            converter.convert(input.toFile())
                .`as`(sourceFormat)
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

    companion object {
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
