package org.edu_sharing.rendering.edusharingRepo.dto

import org.jetbrains.annotations.NotNull

data class RemoveRepositoryRequest(
    @NotNull
    val repoId: String,
    @NotNull
    val url: String,
    @NotNull
    val username: String,
    @NotNull
    val password: String,
)

