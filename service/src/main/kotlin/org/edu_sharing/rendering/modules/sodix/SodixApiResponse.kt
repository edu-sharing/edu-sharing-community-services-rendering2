package org.edu_sharing.rendering.modules.sodix

import com.fasterxml.jackson.annotation.JsonProperty

data class SodixApiResponse(
    @JsonProperty("playoutUrl")
    val playoutUrl: String,
    @JsonProperty("downloadUrl")
    val downloadUrl: String? = null
)
