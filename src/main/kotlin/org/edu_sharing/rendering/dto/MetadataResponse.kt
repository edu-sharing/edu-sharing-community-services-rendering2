package org.edu_sharing.rendering.dto

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "properties")
data class MetadataResponse(
    @JacksonXmlProperty(localName = "appid")
    val appId: String,
    @JacksonXmlProperty(localName = "appcaption")
    val appCaption: String,
    @JacksonXmlProperty(localName = "trustedclient")
    val trustedClient: Boolean,
    val type: String = "SERVICE",
    val host: String,
    val port: Int,
    val scheme: String,
    @JacksonXmlProperty(localName = "public_key")
    val publicKey: String
)
