package org.edu_sharing.rendering.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ErrorMessage(
    @JsonProperty("status")
    var status: Int? = null,
    @JsonProperty("message")
    var message: String? = null
)
