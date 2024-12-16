package org.edu_sharing.rendering.edusharingRepo.dto

import org.jetbrains.annotations.NotNull

data class ActivateOptionalModuleRequest(
    @NotNull
    val repoId: String,
    @NotNull
    val module: String,
    val credentials: Map<String, String>? = null
)
