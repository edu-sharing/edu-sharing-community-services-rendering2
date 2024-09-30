package org.edu_sharing.rendering.modules.h5p.lumi.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class LumiContentResponse(
    @JsonProperty("contentId")
    val contentId: String,
)
