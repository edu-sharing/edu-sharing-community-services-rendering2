package org.edu_sharing.rendering.entity

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id

data class RenderingJob(
    @Id
    val id: ObjectId = ObjectId(),
    var status: JobStatus = JobStatus.QUEUED,
    val esObjectType: String,
    val esObjectId: String,
    val esHash: String,
    val origin: String,
    val mimeType: String,
    val creationTimestamp: Long = System.currentTimeMillis(),
    var finishedTimestamp: Long? = null,
    var subJobs: MutableList<SubJob> = ArrayList(),
)
