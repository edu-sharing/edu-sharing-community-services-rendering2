package org.edu_sharing.rendering.edusharingRepo

import okhttp3.OkHttpClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Typed timeouts of the OkHttp clients behind the repository SDK, bound from `app.repository-http.timeout.*`.
 *
 * The generated `ApiClient` only ships OkHttp's defaults (10 s connect/read/write, no call timeout), so a
 * repository that accepts the connection but never answers could pin a thread (and a dispatcher slot) for
 * as long as the TCP stack allows. [call] is the hard upper bound for one request including redirects and
 * retries; it must stay above [read]/[write] to be meaningful.
 */
@Component
@ConfigurationProperties("app.repository-http.timeout")
class RepositoryHttpTimeoutProperties {
    var connect: Duration = Duration.ofSeconds(10)
    var read: Duration = Duration.ofSeconds(30)
    var write: Duration = Duration.ofSeconds(30)
    var call: Duration = Duration.ofSeconds(60)

    /** Applies the configured timeouts to [builder] (a zero duration means "no timeout" in OkHttp). */
    fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder
        .connectTimeout(connect)
        .readTimeout(read)
        .writeTimeout(write)
        .callTimeout(call)
}
