package org.edu_sharing.rendering.edusharingRepo.entity

data class RepositoryRegistrationConfigItem(
    val url: String,
    val username: String,
    val password: String,
    val optionalModules: List<String> = listOf(),
    val module: MutableMap<String, ModuleSettings> = mutableMapOf()
)

data class ModuleSettings(
    val credentials: Map<String, String>
)
