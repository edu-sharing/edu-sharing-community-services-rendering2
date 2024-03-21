package org.edu_sharing.rendering.controller.external

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.edu_sharing.rendering.service.MetadataService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/public/metadata")
class MetadataController (
    private val metadataService: MetadataService,
){
    @GetMapping(produces = [MediaType.APPLICATION_XML_VALUE])
    fun getMetadata(): String {
        var responseBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        responseBody += "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">"
        val xmlMapper = XmlMapper()
        xmlMapper.enable(SerializationFeature.INDENT_OUTPUT)
        return responseBody + xmlMapper.writeValueAsString(metadataService.getMetadata())
    }
}