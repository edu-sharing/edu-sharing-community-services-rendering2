package org.edu_sharing.rendering.modules.omega

import com.fasterxml.jackson.annotation.JsonProperty

data class OmegaApiResponse(
    @JsonProperty("get")
    val get: OmegaGet
)

data class OmegaGet(
    @JsonProperty("identifier")
    val identifier: String? = null,
    @JsonProperty("streamURL")
    val streamURL: String? = null,
    @JsonProperty("downloadURL")
    val downloadURL: String? = null,
    @JsonProperty("error")
    val error: String? = null
)
