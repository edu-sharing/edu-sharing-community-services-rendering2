package org.edu_sharing.rendering.edusharingRepo.services

import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Typed config for the [RepositoryAppAssertionVerifier], bound from `app.security.app-assertion.*`.
 */
@Component
@ConditionalOnController
@ConfigurationProperties("app.security.app-assertion")
class AppAssertionProperties {
    /** What a repository has to name in `aud`, so that an assertion made for another service is not accepted here. */
    var audience: String = "rendering-service"

    /** The longest lifetime (`exp` - `iat`) accepted; the repository issues assertions valid for 30 seconds. */
    var maxLifetime: Duration = Duration.ofSeconds(60)

    /** Tolerated clock difference between the repository and this service for `iat` and `exp`. */
    var clockSkew: Duration = Duration.ofSeconds(10)
}
