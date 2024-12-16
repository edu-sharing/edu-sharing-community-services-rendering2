package org.edu_sharing.rendering.edusharingRepo.entity

data class RepositoryRegistrationConfigItem(
    val url: String,
    val username: String,
    val password: String,
    val optionalModules: List<String>,
    val module: Map<String, ModuleSettings>
)

data class ModuleSettings(
    val credentials: Map<String, String>
)
