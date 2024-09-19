package org.edu_sharing.rendering.renderingJobs.dto

import com.fasterxml.jackson.annotation.JsonProperty
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.renderingJobs.entity.JobStatus

data class JobProgressInfo(
    @JsonProperty("quality")
    val quality: Int = 0,
    @JsonProperty("progress")
    var progress: Long = 0,
    @JsonProperty("objectLink")
    var objectLink: ObjectLink? = null,
    @JsonProperty("status")
    var status: JobStatus
)
