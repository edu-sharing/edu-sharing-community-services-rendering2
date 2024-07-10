package org.edu_sharing.rendering.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ObjectLink (
    @JsonProperty("width")
    var width: Int = 0,
    @JsonProperty("height")
    var height: Int = 0,
    @JsonProperty("link")
    val link: String,
    @JsonProperty("isHighestQuality")
    var isHighestQuality: Boolean = false,
)