package org.edu_sharing.rendering.edusharingRepo.repository

import org.edu_sharing.rendering.edusharingRepo.entity.RendererKeyConfig
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface RendererKeyConfigRepository: MongoRepository<RendererKeyConfig, String> {
}
