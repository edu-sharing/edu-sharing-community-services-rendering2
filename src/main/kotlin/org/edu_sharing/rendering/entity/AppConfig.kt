package org.edu_sharing.rendering.entity

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document
data class AppConfig(
    @Id
    val id: ObjectId = ObjectId(),
    val repoPublicKey: String? = null,
    val appId: String,
    val appCaption: String,
    val trustedClient: Boolean,
    val type: String = "SERVICE",
    val host: String,
    val port: Int,
    val scheme: String,
    var privateKey: String? = null,
    var publicKey: String? = null
)
