package org.edu_sharing.rendering.dto

import com.fasterxml.jackson.annotation.JsonProperty
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.modules.RenderModules

data class JobInfoReply(
    @JsonProperty("jobs")
    val jobs: MutableList<JobProgressInfo>,
    @JsonProperty("module")
    val module: RenderModules = RenderModules.IMAGE,
    @JsonProperty("status")
    val status: JobStatus
)
