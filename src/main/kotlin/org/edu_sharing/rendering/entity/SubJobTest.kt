package org.edu_sharing.rendering.entity

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference

@Document
data class SubJobTest(
    @Id
    var id: ObjectId = ObjectId(),
    @DocumentReference(lazy = true)
    var parent: JobTest,
    var name: String
)
