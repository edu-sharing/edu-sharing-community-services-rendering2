package org.edu_sharing.rendering.modules.sodix

import com.fasterxml.jackson.annotation.JsonProperty

data class SodixApiResponse(
    @JsonProperty("url")
    val url: String,
)
