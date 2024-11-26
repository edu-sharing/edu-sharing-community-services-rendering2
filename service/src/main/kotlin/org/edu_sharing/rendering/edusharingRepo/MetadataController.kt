package org.edu_sharing.rendering.edusharingRepo

import org.apache.commons.io.output.ByteArrayOutputStream
import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.edusharingRepo.services.MetadataService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@ConditionalOnMaster
@RequestMapping("/public/metadata")
@ConditionalOnProperty(name = ["app.repository.registration.enabled"], havingValue = "true")
class MetadataController (
    private val metadataService: MetadataService,
    private val appInfo: AppInfo
){
    @GetMapping(produces = [MediaType.APPLICATION_XML_VALUE])
    fun getMetadata(): String {
        var responseBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        responseBody += "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">"

        val metadata = metadataService.getConfig()
        val outputStream = ByteArrayOutputStream()
        val props = Properties()

        props["appid"] = appInfo.appId
        props["appcaption"] = appInfo.appCaption
        props["type"] = "RENDERINGSERVICE_2"
        props["host"] = appInfo.public.url
        props["port"] = appInfo.public.port.toString()
        props["trustedclient"] = "true"
        props["public_key"] = metadata.publicKey
        props.storeToXML(outputStream, "rendering application file for application type lms", "UTF-8")
        return String(outputStream.toByteArray())
    }
}
