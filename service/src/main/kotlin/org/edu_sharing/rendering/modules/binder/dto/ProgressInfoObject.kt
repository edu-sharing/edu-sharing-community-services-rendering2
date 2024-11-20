package org.edu_sharing.rendering.modules.binder.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ProgressInfoObject(
    @JsonProperty("current")
    val current: Long,
    @JsonProperty("total")
    val total: Long
)