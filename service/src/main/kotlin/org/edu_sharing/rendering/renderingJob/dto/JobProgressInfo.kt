package org.edu_sharing.rendering.renderingJob.dto

import com.fasterxml.jackson.annotation.JsonProperty
import org.edu_sharing.rendering.core.dto.ErrorMessage
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus

data class JobProgressInfo(
    @JsonProperty("quality")
    val quality: Int = 0,
    @JsonProperty("progress")
    var progress: Long = 0,
    @JsonProperty("objectLink")
    var objectLink: ObjectLink? = null,
    @JsonProperty("status")
    var status: SubJobStatus,
    @JsonProperty("additionalData")
    var additionalData: Map<String, String>? = null,
    var error: ErrorMessage? = null
)
