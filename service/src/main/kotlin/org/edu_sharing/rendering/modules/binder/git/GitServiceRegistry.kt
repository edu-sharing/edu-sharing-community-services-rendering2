package org.edu_sharing.rendering.modules.binder.git

import org.springframework.lang.Nullable
import org.springframework.stereotype.Component

@Component
class GitServiceRegistry(@Nullable private val services: List<GitService>) {
    fun getService(url: String): GitService? {
        return services.firstOrNull { it.identifyUrl(url) }
    }
}