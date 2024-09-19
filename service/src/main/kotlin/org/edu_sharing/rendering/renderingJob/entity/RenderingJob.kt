package org.edu_sharing.rendering.renderingJob.entity

import org.bson.types.ObjectId
import org.edu_sharing.rendering.modules.RenderModules
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.ReadOnlyProperty
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference
import java.util.*

@Document
data class RenderingJob(
    @Id
    val id: ObjectId = ObjectId(),
    @Indexed
    var status: JobStatus = JobStatus.QUEUED,
    var module: RenderModules,
    val esObjectType: String,
    val esObjectId: String,
    val repoId: String,
    val esHash: String,
    val mimeType: String,
    val nodeVersion: String,
    val size: Long? = null,
    val creationTimestamp: Long = System.currentTimeMillis(),
    var finishedTimestamp: Long? = null,
    @ReadOnlyProperty
    @DocumentReference(lazy = true, lookup = "{'parent':?#{#self._id} }")
    var subJobs: MutableList<SubJob> = ArrayList(),
    @Indexed(expireAfter = "86400")
    @LastModifiedDate
    var lastModifiedDate: Date? = null,
    @Version
    val version: Int? = null,
    val conversionType: Boolean = false
)
