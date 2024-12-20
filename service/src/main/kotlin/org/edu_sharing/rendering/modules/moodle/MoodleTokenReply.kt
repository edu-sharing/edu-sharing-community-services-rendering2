package org.edu_sharing.rendering.modules.moodle

import com.fasterxml.jackson.annotation.JsonProperty

data class MoodleTokenReply(
    @JsonProperty("token")
    val token: String,
    @JsonProperty("privatetoken")
    val privatetoken: String?
)
