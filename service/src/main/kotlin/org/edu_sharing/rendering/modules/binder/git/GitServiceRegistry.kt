package org.edu_sharing.rendering.modules.binder.git

import org.springframework.stereotype.Component

@Component
class GitServiceRegistry(private val services: List<GitService> = emptyList()) {
    fun getService(url: String): GitService? {
        return services.firstOrNull { it.identifyUrl(url) }
    }
}