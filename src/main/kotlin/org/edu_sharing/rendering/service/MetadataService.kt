package org.edu_sharing.rendering.service

import org.bson.types.ObjectId
import org.edu_sharing.rendering.entity.AppConfig
import org.edu_sharing.rendering.exception.EntryNotFoundException
import org.edu_sharing.rendering.repository.mongo.AppConfigRepository
import org.springframework.stereotype.Service

@Service
class MetadataService(
    private val repository: AppConfigRepository
) {
    fun getMetadata(): AppConfig {
        val appConfigs = repository.findById("0")
            .orElseThrow { throw EntryNotFoundException("No config found in database.") }
        return appConfigs
    }
}