package org.edu_sharing.rendering.core.dto

import org.edu_sharing.generated.repository.backend.services.rest.client.model.User
import org.jetbrains.annotations.NotNull

data class RenderDataRequest(
    @NotNull
    val nodeId: String,
    @NotNull
    val repoId: String,
    @NotNull
    val securedNode: String,
    @NotNull
    val signature: String,
    @NotNull
    val userData: RequestUserData
)


