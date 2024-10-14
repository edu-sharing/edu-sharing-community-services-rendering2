package org.edu_sharing.rendering.renderingJob.entity

import org.bson.types.ObjectId
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference
import java.util.Date

@Document
@CompoundIndexes(
    CompoundIndex(name="id", def="{'status': 1, 'routingKey': 1, 'priority': 1, '_id': 1}"),
)
data class SubJob(
    @Id
    var id: ObjectId = ObjectId(),
    var routingKey: String,
    @CreatedDate
    var createdDate: Date? = null,
    @Indexed
    var status: JobStatus = JobStatus.QUEUED,
    val quality: Int = 0,
    var progress: Int = 0,
    var message: String? = null,
    @DocumentReference(lazy = true)
    var parent: RenderingJob,
    @Version
    val version: Int? = null,
    val priority: Int = 0
)
