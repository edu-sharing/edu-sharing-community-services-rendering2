package org.edu_sharing.rendering.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document

@Document
data class AppConfig(
    @Id
    val id: String = "0",
    @Version
    val version: Long? = null,
    var repoPublicKey: String? = null,
    var privateKey: String? = null,
    var publicKey: String? = null
)
