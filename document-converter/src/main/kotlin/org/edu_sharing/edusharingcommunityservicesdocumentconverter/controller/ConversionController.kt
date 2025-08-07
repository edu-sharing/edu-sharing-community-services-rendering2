package org.edu_sharing.edusharingcommunityservicesdocumentconverter.controller

import org.edu_sharing.edusharingcommunityservicesdocumentconverter.service.ConversionService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
/**
 * Controller responsible for handling file conversion requests.
 * This controller provides an endpoint for converting an input file into a specified target format.
 *
 * @constructor Creates a ConversionController with the provided ConversionService for processing conversions.
 * @param service The service that contains the logic for file conversion and format handling.
 */
@RestController
@RequestMapping("/conversion")
class ConversionController (
    private val service: ConversionService
) {
    /**
     * Converts an uploaded file to the specified target format.
     *
     * This endpoint accepts a multipart file upload and converts it to the requested output format.
     * The converted file is returned as a downloadable attachment with appropriate headers set.
     *
     * @param targetExtension The desired output format extension (defaults to "pdf" if not specified).
     *                       This parameter determines the target format for the conversion.
     * @param inputFile The multipart file to be converted. Must be a valid file upload.
     *
     * @return ResponseEntity containing the converted file as a byte array with appropriate
     *         HTTP headers including Content-Disposition for download and Content-Type
     *         matching the target format's media type.
     *
     * @throws IllegalArgumentException if the target extension is not supported
     * @throws RuntimeException if the file conversion process fails
     *
     * @since 1.0
     */

    @PostMapping
    fun convert(
        @RequestParam(name = "format", defaultValue = "pdf") targetExtension: String,
        @RequestParam("file") inputFile: MultipartFile
    ): ResponseEntity<*> {
        val fileName = service.getFileName(inputFile)
        val sourceFormat = service.getSourceFormat(fileName)
        val targetFormat = service.getTargetFormat(targetExtension)
        val outputStream = service.convert(inputFile, sourceFormat, targetFormat)

        val headers = HttpHeaders()
        val targetFilename = "${fileName.substringBefore(".")}.${targetFormat.extension}"
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$targetFilename")
        headers.contentType = MediaType.parseMediaType(targetFormat.mediaType)
        return ResponseEntity.ok().headers(headers).body(outputStream.toByteArray())
    }
}
