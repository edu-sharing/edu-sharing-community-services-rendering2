package org.edu_sharing.rendering.renderingJob.entity

import org.bson.types.ObjectId
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference
import java.time.Instant
import java.util.*

@Document
@CompoundIndexes(
    CompoundIndex(name="createdDate", def="{'status': 1, 'routingKey': 1, 'priority': 1, 'createdDate': 1}"),
)
data class SubJob(
    @Id
    var id: ObjectId = ObjectId(),
    var routingKey: String,
    @CreatedDate
    var createdDate: Date? = null,
    @Indexed
    var status: SubJobStatus = SubJobStatus.QUEUED,
    var quality: Int = 0,
    var progress: Int = 0,
    var message: String? = null,
    // Trägt SubJobRepository.findByParentId(In) (u.a. die Admin-Jobliste) – ohne Index scannt
    // Mongo pro Lookup die komplette subJob-Collection.
    @Indexed
    @DocumentReference(lazy = true)
    var parent: RenderingJob,
    @Indexed(expireAfter = "8d")
    @LastModifiedDate
    var lastModifiedDate: Instant? = null,
    @Version
    val version: Int? = null,
    val priority: Int = 0,
    var additionalData: Map<String, String>? = null,
    var errorMessage: String? = null
)
