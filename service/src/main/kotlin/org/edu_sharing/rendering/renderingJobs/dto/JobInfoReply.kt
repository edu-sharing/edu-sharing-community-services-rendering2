package org.edu_sharing.rendering.renderingJobs.dto

import com.fasterxml.jackson.annotation.JsonProperty
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.renderingJobs.entity.JobStatus

data class JobInfoReply(
    @JsonProperty("jobs")
    val jobs: MutableList<JobProgressInfo>,
    @JsonProperty("module")
    val module: RenderModules = RenderModules.IMAGE,
    @JsonProperty("status")
    val status: JobStatus
)
