package org.edu_sharing.rendering.dto

import org.edu_sharing.rendering.config.annotation.ConditionalOnController
import org.edu_sharing.rendering.entity.JobStatus

data class JobProgressInfo(
    val quality: Int = 0,
    var progress: Long = 0,
    var objectLink: ObjectLink? = null,
    var status: JobStatus
)
