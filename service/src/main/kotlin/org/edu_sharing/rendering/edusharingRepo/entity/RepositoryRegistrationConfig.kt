package org.edu_sharing.rendering.edusharingRepo.entity

import org.edu_sharing.rendering.edusharingRepo.dto.RegisterRepositoryRequest
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.repository.registration")
class RepositoryRegistrationConfig {

    var id: Map<String, RepositoryRegistrationConfigItem> = emptyMap()

    fun getAllRegistrations(): List<Pair<RegisterRepositoryRequest, List<String>>> {
        return id.entries.stream()
            .map {
                Pair(
                    RegisterRepositoryRequest(
                        url = it.value.url,
                        username = it.value.username,
                        password = it.value.password
                    ),
                    it.value.optionalModules
                )
            }
            .toList()
    }
}
