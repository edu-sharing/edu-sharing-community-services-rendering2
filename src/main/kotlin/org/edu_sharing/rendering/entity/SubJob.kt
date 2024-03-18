package org.edu_sharing.rendering.entity

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference

@Document
data class SubJob(
    @Id
    var id: ObjectId = ObjectId(),
    var routingKey: String,
    var status: JobStatus = JobStatus.QUEUED,
    val quality: Int = 0,
    var progress: Int = 0,
    var message: String? = null,
    @DocumentReference(lazy = true)
    var parent: RenderingJob
)
