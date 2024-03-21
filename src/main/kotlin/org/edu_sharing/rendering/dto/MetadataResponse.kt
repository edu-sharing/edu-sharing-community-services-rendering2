package org.edu_sharing.rendering.dto

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "properties")
data class MetadataResponse(
    val appid: String,
    val appcaption: String,
    val trustedclient: Boolean,
    val type: String = "SERVICE",
    val host: String,
    val port: Int,
    val public_key: String
)
