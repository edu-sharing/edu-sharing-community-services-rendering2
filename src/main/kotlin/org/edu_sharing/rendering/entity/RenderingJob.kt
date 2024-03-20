package org.edu_sharing.rendering.entity

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.ReadOnlyProperty
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference

@Document
data class RenderingJob(
    @Id
    val id: ObjectId = ObjectId(),
    @Indexed
    var status: JobStatus = JobStatus.QUEUED,
    val esObjectType: String,
    val esObjectId: String,
    val esHash: String,
    val origin: String,
    val mimeType: String,
    val creationTimestamp: Long = System.currentTimeMillis(),
    var finishedTimestamp: Long? = null,
    @ReadOnlyProperty
    @DocumentReference(lazy = true, lookup = "{'parent':?#{#self._id} }")
    var subJobs: MutableList<SubJob> = ArrayList(),
)
