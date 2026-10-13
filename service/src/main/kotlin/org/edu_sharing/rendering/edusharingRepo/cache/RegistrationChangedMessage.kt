package org.edu_sharing.rendering.edusharingRepo.cache

/**
 * Broadcast payload: the registration of [repoId] changed (created, updated, module/CSP change, deleted).
 * [origin] is the instance that made the change; it already updated its own cache and ignores the message.
 */
data class RegistrationChangedMessage(
    val repoId: String,
    val origin: String
)
