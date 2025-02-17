package org.edu_sharing.rendering.core.dto

import org.jetbrains.annotations.NotNull

data class RenderDataRequest(
    @NotNull
    val nodeId: String,
    @NotNull
    val size: Long,
    @NotNull
    val type: String,
    val hash: String?,
    val mimeType: String?,
    @NotNull
    val version: String,
    @NotNull
    val repoId: String,
    val url: String?,
    val title: String?,
    val userData: RequestUserData?,
    val replicationSource: String?,
    val replicationSourceId: String?,
    val replicationSourceFlag: Boolean = false,
    val resourceType: String? = null
)
