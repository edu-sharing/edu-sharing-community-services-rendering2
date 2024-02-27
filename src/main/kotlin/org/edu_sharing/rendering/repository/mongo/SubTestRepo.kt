package org.edu_sharing.rendering.repository.mongo

import org.bson.types.ObjectId
import org.edu_sharing.rendering.entity.SubJobTest
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface SubTestRepo: MongoRepository<SubJobTest, ObjectId> {
}