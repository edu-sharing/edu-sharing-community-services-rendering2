package org.edu_sharing.rendering.renderingJob.dto

import com.fasterxml.jackson.annotation.JsonProperty
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.renderingJob.entity.JobStatus

data class JobInfoReply(
    @JsonProperty("jobs")
    val jobs: MutableList<JobProgressInfo>,
    @JsonProperty("module")
    val module: RenderModules = RenderModules.IMAGE,
    @JsonProperty("status")
    val status: JobStatus
)
