package org.edu_sharing.rendering.modules.h5p.lumi

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Settings of the lumi reverse proxy ([LumiProxyService]), bound from `app.lumi.proxy.*`. The proxy's circuit
 * breaker and bulkhead (guard `lumi-proxy`) are configured under `app.resilience.modules.lumi-proxy.*`.
 */
@Component
@ConfigurationProperties("app.lumi.proxy")
class LumiProxyProperties {
    /**
     * Upper bound for lumi to deliver status + headers of a proxied response. Every proxied request holds a
     * Tomcat thread for this long at most, so it is deliberately short; the body streams afterwards and is not
     * covered. A timeout is answered with 504.
     */
    var responseHeadersTimeout: Duration = Duration.ofSeconds(20)
}
