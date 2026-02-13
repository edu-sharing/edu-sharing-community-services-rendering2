package org.edu_sharing.rendering.edusharingRepo.entity

data class RepositoryRegistrationConfigItem(
    val url: String,
    val username: String,
    val password: String,
    var quota: Long = 0,
    val optionalModules: List<String> = listOf(),
    val module: MutableMap<String, ModuleSettings> = mutableMapOf()
)

data class ModuleSettings(
    var credentials: Map<String, String> = emptyMap(),
    var cspHeader: String? = null
)
