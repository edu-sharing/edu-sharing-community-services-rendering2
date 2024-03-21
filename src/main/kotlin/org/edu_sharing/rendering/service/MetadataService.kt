package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.MetadataResponse
import org.edu_sharing.rendering.exception.EntryNotFoundException
import org.edu_sharing.rendering.repository.mongo.AppConfigRepository
import org.springframework.stereotype.Service

@Service
class MetadataService (
    private val repository: AppConfigRepository
) {
    fun getMetadata(): MetadataResponse {
        val appConfigs = repository.findAll()
        if (appConfigs.size == 0) {
            throw EntryNotFoundException("No config found in database.")
        }
        val appConfig = appConfigs[0]
        return MetadataResponse(
            appid = appConfig.appId,
            appcaption = appConfig.appCaption,
            trustedclient = true,
            host = appConfig.host,
            port = appConfig.port,
            public_key = appConfig.publicKey ?: ""
        )
    }
}