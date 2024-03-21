package org.edu_sharing.rendering.dto


data class MetadataResponse(
    val appId: String,
    val appCaption: String,
    val trustedClient: Boolean,
    val type: String = "SERVICE",
    val host: String,
    val port: Int,
    val publicKey: String
)
