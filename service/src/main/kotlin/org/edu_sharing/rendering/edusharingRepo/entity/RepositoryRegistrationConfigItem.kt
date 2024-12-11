package org.edu_sharing.rendering.edusharingRepo.entity

data class RepositoryRegistrationConfigItem(
    val url: String,
    val username: String,
    val password: String,
    val optionalModules: List<String>
)
