package org.edu_sharing.edusharingcommunityservicesdocumentconverter.controller

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.edu_sharing.edusharingcommunityservicesdocumentconverter.service.ConversionService

@RestController
@RequestMapping("/conversion")
class ConversionController (
    private val service: ConversionService
){
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