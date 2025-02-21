package org.edu_sharing.rendering.edusharingRepo.entity

data class RepositoryRegistrationConfigItem(
    val url: String,
    val username: String,
    val password: String,
    val optionalModules: List<String> = listOf(),
    val module: Map<String, ModuleSettings> = emptyMap()
)

data class ModuleSettings(
    val credentials: Map<String, String>
)
