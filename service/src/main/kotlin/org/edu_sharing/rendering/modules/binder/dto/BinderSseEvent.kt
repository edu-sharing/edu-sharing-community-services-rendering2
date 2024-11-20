package org.edu_sharing.rendering.modules.binder.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class BinderSseEvent(
    @JsonProperty("phase")
    val phase: String,
    @JsonProperty("message")
    val message: String,
    @JsonProperty("imageName")
    val imageName: String? = null,
    @JsonProperty("progress")
    val progress: ProgressEntry? = null,
    @JsonProperty("url")
    val url: String? = null,
    @JsonProperty("token")
    val token: String? = null
)
