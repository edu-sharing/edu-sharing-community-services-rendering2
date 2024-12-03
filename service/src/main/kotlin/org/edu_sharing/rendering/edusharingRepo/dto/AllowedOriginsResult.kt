package org.edu_sharing.rendering.edusharingRepo.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class AllowedOriginsResult(
    @JsonProperty("allowedOrigins")
    var allowedOrigins: List<String>
)
