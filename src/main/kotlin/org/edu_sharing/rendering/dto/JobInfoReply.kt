package org.edu_sharing.rendering.dto

import org.edu_sharing.rendering.entity.JobStatus

data class JobInfoReply(
    val jobs: MutableList<JobProgressInfo>,
    val module: RenderModules = RenderModules.IMAGE,
    val status: JobStatus
)
