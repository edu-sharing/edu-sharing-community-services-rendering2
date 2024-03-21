package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.MetadataResponse
import org.edu_sharing.rendering.exception.EntryNotFoundException
import org.edu_sharing.rendering.repository.mongo.AppConfigRepository
import org.springframework.stereotype.Service

@Service
class MetadataService (
    private val repository: AppConfigRepository
) {
    fun getMetadata(): AppConfig {
        val appConfigs = repository.findAll()
        if (appConfigs.size == 0) {
            throw EntryNotFoundException("No config found in database.")
        }
        return appConfigs[0];
    }
}