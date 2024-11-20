package org.edu_sharing.rendering.edusharingRepo.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed

data class RepositoryRegistration(
    @Id
    val id: String? = null,

    @Indexed(unique = true)
    val repoId: String,

    var url: String,
    var publicKey: String
)
