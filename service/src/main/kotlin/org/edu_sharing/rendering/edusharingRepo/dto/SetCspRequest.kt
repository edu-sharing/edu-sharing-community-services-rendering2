package org.edu_sharing.rendering.edusharingRepo.dto

import jakarta.validation.constraints.NotNull

data class SetCspRequest(
    @field:NotNull
    val repoId: String,
    @field:NotNull
    val module: String,
    val cspHeaderValue: String?
)
