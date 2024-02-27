package org.edu_sharing.rendering.repository.mongo

import org.bson.types.ObjectId
import org.edu_sharing.rendering.entity.JobTest
import org.springframework.data.mongodb.repository.MongoRepository

interface TestRepo: MongoRepository<JobTest, ObjectId> {
}