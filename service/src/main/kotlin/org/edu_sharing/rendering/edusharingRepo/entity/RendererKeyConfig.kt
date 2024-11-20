package org.edu_sharing.rendering.edusharingRepo.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document

@Document
data class RendererKeyConfig(
    @Id
    val id: String = "0",
    @Version
    val version: Long? = null,
    var privateKey: String? = null,
    var publicKey: String? = null
)
