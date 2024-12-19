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
import java.nio.charset.StandardCharsets
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
        val metadata = metadataService.getConfig()
        val outputStream = ByteArrayOutputStream()
        val props = Properties()

        props["appid"] = appInfo.appId
        props["appcaption"] = appInfo.appCaption
        props["type"] = "RENDERINGSERVICE_2"
        props["protocol"] = appInfo.public.protocol
        props["host"] = appInfo.public.host
        props["port"] = appInfo.public.port.toString()
        props["contenturl"] = appInfo.public.url
        props["trustedclient"] = "true"
        props["public_key"] = metadata.publicKey
        props.storeToXML(outputStream, "rendering application file for application type lms", StandardCharsets.UTF_8)
        return outputStream.toString(StandardCharsets.UTF_8)
    }
}
