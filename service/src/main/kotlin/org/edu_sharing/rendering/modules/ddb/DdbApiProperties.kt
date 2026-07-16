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
 */
@Component
@ConfigurationProperties("app.module.ddb")
class DdbApiProperties {
    var restApiBaseUrl: String = "https://api.deutsche-digitale-bibliothek.de"
    var iiifApiBaseUrl: String = "https://iiif.deutsche-digitale-bibliothek.de/image/2"
}
