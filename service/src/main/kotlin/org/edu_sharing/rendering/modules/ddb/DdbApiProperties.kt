package org.edu_sharing.rendering.modules.ddb

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Base URLs of the two DDB (Deutsche Digitale Bibliothek) backend APIs, bound from
 * `app.module.ddb.*`. The defaults are the production endpoints, so behaviour is unchanged
 * unless explicitly overridden — the override exists so the load test (and unit tests) can
 * point DDB at a mock, since these hosts were previously hard-coded in [DdbRenderModule].
 *
 * - [restApiBaseUrl]  → the item metadata REST API (`/items/{remoteId}`).
 * - [iiifApiBaseUrl]  → the IIIF image API (`/{binaryRef}/info.json`) and the public
 *   `linkTemplate` returned to the client.
 * - [authMode]        → how the consumer key (`apiToken`) is sent, see [DdbAuthMode].
 *
 * Auth mode: the default [DdbAuthMode.QUERY] is the long-proven behaviour (`?oauth_consumer_key=...` on both the
 * REST and the IIIF API). [DdbAuthMode.HEADER] (`Authorization: OAuth oauth_consumer_key="..."` on the REST API,
 * which keeps the key out of URIs and access logs) is **unverified** against the live API: the OpenAPI spec of the
 * `/2` API documents only Bearer-JWT for authenticated endpoints and does not mention `oauth_consumer_key` at all,
 * and an invalid key answers 403 in both variants, so it cannot be checked without a valid key. Enable it only
 * after testing with a real key. The IIIF API always receives the key as query parameter, in both modes.
 * Error paths are sanitised ([org.edu_sharing.rendering.utils.LogSanitizer]) either way.
 */
@Component
@ConfigurationProperties("app.module.ddb")
class DdbApiProperties {
    var restApiBaseUrl: String = "https://api.deutsche-digitale-bibliothek.de"
    var iiifApiBaseUrl: String = "https://iiif.deutsche-digitale-bibliothek.de/image/2"
    var authMode: DdbAuthMode = DdbAuthMode.QUERY
}

/** How the DDB consumer key is transmitted to the REST API (`app.module.ddb.auth-mode`). */
enum class DdbAuthMode {
    /** `?oauth_consumer_key=<key>` (default, proven in production). */
    QUERY,

    /** `Authorization: OAuth oauth_consumer_key="<key>"` on the REST API (opt-in, unverified). */
    HEADER,
}
