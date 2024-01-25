package org.edu_sharing.rendering.dto

import org.jetbrains.annotations.NotNull

data class RenderDataRequest(
    @NotNull
    val token: String,
    @NotNull
    val nodeId: String,
    @NotNull
    val size: Long,
    val version: String,
    @NotNull
    val type: String,
    @NotNull
    val hash: String,
    @NotNull
    val extension: String,
    @NotNull
    val mimeType: String
)
