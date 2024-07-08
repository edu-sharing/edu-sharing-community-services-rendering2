package org.edu_sharing.rendering.processing.h5p

import com.fasterxml.jackson.annotation.JsonProperty

data class LumiContentResponse(
    @JsonProperty("contentId")
    val contentId: String,
)
