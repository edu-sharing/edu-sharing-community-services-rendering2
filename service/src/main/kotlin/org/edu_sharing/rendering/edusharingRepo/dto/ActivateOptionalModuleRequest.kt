package org.edu_sharing.rendering.edusharingRepo.dto

import org.jetbrains.annotations.NotNull

data class ActivateOptionalModuleRequest(
    @NotNull
    val repoId: String,
    @NotNull
    val modules: List<String>,
)
