package org.edu_sharing.rendering.core.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ObjectLink (
    @JsonProperty("width")
    var width: Int = 0,
    @JsonProperty("height")
    var height: Int = 0,
    @JsonProperty("link")
    val link: String,
)