package org.edu_sharing.rendering.edusharingRepo.cache

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Typed config for the pod-local caches in front of the repository registrations and keys, bound from
 * `app.registration-cache.*`.
 *
 * The cache is kept consistent across pods by a broadcast on every registration change
 * ([RegistrationCacheBroadcaster]); [expireAfterWrite] is only the safety net for a broadcast a pod missed
 * (the listener queue is anonymous and gone while the connection is down), so it bounds how long a pod may
 * serve a stale registration.
 */
@Component
@ConfigurationProperties("app.registration-cache")
class RegistrationCacheProperties {
    /** Max entries per cache. One entry per registered repository, so the default is generous. */
    var maximumSize: Long = 1000

    /** Max age of an entry, whatever the broadcasts did. */
    var expireAfterWrite: Duration = Duration.ofMinutes(5)
}
