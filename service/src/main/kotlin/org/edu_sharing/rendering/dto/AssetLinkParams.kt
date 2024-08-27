package org.edu_sharing.rendering.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class AssetLinkParams(
    @JsonProperty("nodeId")
    val repoId: String,
    @JsonProperty("nodeId")
    val nodeId: String,
    @JsonProperty("hash")
    val hash: String,
    @JsonProperty("quality")
    val quality: Int,
    @JsonProperty("type")
    val type: String,
    @JsonProperty("mimeType")
    val mimeType: String
)
