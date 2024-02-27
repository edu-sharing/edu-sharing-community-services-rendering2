package org.edu_sharing.rendering.entity

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.ReadOnlyProperty
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference

@Document
data class JobTest(
    @Id
    var id: ObjectId = ObjectId(),
    var name: String,
    @ReadOnlyProperty
    @DocumentReference(lazy = true, lookup = "{'parent':?#{#self._id} }")
    var subJobs: MutableList<SubJobTest>? = null
)
