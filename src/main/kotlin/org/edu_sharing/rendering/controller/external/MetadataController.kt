package org.edu_sharing.rendering.controller.external

import org.apache.commons.io.output.ByteArrayOutputStream
import org.edu_sharing.rendering.service.MetadataService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/public/metadata")
class MetadataController (
    private val metadataService: MetadataService,
){
    @GetMapping(produces = [MediaType.APPLICATION_XML_VALUE])
    fun getMetadata(): String {
        var responseBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        responseBody += "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">"

        val metadata = metadataService.getMetadata()
        val outputStream = ByteArrayOutputStream()
        val props = Properties()

        props.put("appid", metadata.appId)
        props.put("appcaption", metadata.appCaption)
        props.put("type", metadata.type)
        props.put("host", metadata.host)
        props.put("port", metadata.port.toString())
        props.put("trustedclient", metadata.trustedClient.toString())
        props.put("public_key", metadata.publicKey)
        props.storeToXML(outputStream, "rendering application file for application type lms", "UTF-8")
        return String(outputStream.toByteArray())
    }
}