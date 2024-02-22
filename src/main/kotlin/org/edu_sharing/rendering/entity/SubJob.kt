package org.edu_sharing.rendering.entity

data class SubJob(
    var status: JobStatus = JobStatus.QUEUED,
    val quality: Int = 0
)
