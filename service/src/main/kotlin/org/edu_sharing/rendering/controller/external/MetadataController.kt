package org.edu_sharing.rendering.controller.external

import org.apache.commons.io.output.ByteArrayOutputStream
import org.edu_sharing.rendering.service.MetadataService
import org.springframework.beans.factory.annotation.Value
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
    @Value("\${app.public.url}")
    lateinit var publicUrl: String

    @Value("\${app.public.port}")
    lateinit var port: String

    @Value("\${app.appId}")
    lateinit var appId: String

    @Value("\${app.appCaption}")
    lateinit var appCaption: String
    @GetMapping(produces = [MediaType.APPLICATION_XML_VALUE])
    fun getMetadata(): String {
        var responseBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        responseBody += "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">"

        val metadata = metadataService.getConfig()
        val outputStream = ByteArrayOutputStream()
        val props = Properties()

        props["appid"] = appId
        props["appcaption"] = appCaption
        props["type"] = "SERVICE2"
        props["host"] = publicUrl
        props["port"] = port
        props["trustedclient"] = "true"
        props["public_key"] = metadata.publicKey
        props.storeToXML(outputStream, "rendering application file for application type lms", "UTF-8")
        return String(outputStream.toByteArray())
    }
}