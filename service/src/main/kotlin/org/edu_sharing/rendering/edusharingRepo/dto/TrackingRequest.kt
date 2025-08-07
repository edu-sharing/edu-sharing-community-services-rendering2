package org.edu_sharing.rendering.edusharingRepo.dto

import jakarta.validation.constraints.NotNull

data class TrackingRequest(
    @NotNull
    val repoId: String,
    @NotNull
    val eventType: String,
    @NotNull
    val nodeId: String,
)
