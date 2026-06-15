package org.edu_sharing.rendering.edusharingRepo.cors

import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnMaster
class CorsSyncScheduler(
    private val corsSyncService: CorsSyncService
) {
    @Scheduled(
        fixedDelayString = $$"${app.cors.sync.schedule}",
        initialDelayString = $$"${app.cors.sync.schedule}"
    )
    fun syncCorsConfig() {
        corsSyncService.syncAllowedOriginsWithAllRepositories()
    }
}
