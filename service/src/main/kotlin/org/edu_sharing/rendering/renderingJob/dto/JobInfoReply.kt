package org.edu_sharing.rendering.renderingJob.dto

import com.fasterxml.jackson.annotation.JsonProperty
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus

data class JobInfoReply(
    @JsonProperty("jobs")
    val jobs: MutableList<JobProgressInfo>,
    @JsonProperty("module")
    val module: String? = null,
    @JsonProperty("status")
    val status: RenderingJobStatus,
    val userMessage: String? = null,
)
