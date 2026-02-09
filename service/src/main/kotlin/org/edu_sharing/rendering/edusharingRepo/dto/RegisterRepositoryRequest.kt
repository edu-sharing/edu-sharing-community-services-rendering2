package org.edu_sharing.rendering.edusharingRepo.dto

import org.jetbrains.annotations.NotNull

data class RegisterRepositoryRequest(
    @NotNull
    val url: String,
    @NotNull
    val username: String,
    @NotNull
    val password: String,
    val quota: Long = 0
)

