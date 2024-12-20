package org.edu_sharing.rendering.edusharingRepo.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document
data class RepositoryRegistration(
    @Id
    var id: String? = null,

    @Indexed(unique = true)
    var repoId: String,

    var url: String,
    var publicKey: String,
    var domains: List<String>? = emptyList(),
    var optionalModules: MutableList<String> = mutableListOf(),
    val module: MutableMap<String, ModuleSettings> = mutableMapOf<String, ModuleSettings>()
)
