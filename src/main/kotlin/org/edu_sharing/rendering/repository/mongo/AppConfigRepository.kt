package org.edu_sharing.rendering.repository.mongo

import org.bson.types.ObjectId
import org.edu_sharing.rendering.entity.AppConfig
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface AppConfigRepository: MongoRepository<AppConfig, String> {
}