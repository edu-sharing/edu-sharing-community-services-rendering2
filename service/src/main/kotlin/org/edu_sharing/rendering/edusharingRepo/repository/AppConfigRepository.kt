package org.edu_sharing.rendering.edusharingRepo.repository

import org.edu_sharing.rendering.edusharingRepo.config.AppConfig
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface AppConfigRepository: MongoRepository<AppConfig, String> {
}
