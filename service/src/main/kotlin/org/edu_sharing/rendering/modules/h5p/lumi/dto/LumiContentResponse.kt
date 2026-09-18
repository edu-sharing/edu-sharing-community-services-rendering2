package org.edu_sharing.rendering.modules.h5p.lumi.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class LumiContentResponse(
    @JsonProperty("contentId")
    val contentId: String,
    /**
     * Bytes this package occupies in lumi's per-package library cache; `null` both on the shared
     * global library storage (the package has no library set of its own) and from a lumi older than
     * the per-package cache, which does not send the field at all - hence nullable rather than a
     * defaulted primitive, which would fail to deserialize such a response outright.
     */
    @JsonProperty("libraryBytes")
    val libraryBytes: Long? = null,
)
