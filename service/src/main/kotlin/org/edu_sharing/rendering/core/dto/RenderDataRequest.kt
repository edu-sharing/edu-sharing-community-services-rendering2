package org.edu_sharing.rendering.core.dto

import org.jetbrains.annotations.NotNull

data class RenderDataRequest(
    @param:NotNull
    val nodeId: String,
    @param:NotNull
    val repoId: String,
    @param:NotNull
    val securedNode: String,
    @param:NotNull
    val signature: String,
    @param:NotNull
    val signatureAlgorithm: String,
    val eventType: String = "VIEW_MATERIAL",
)


