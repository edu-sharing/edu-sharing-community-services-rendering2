package org.edu_sharing.rendering.modules.moodle

import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import java.net.URI

/**
 * Points the builder at the Moodle `baseurl`. With a `publicurl` set - baseurl then usually points
 * into the docker network - every request carries publicurl's host as Host header: Moodle answers a
 * request whose host differs from its `$CFG->wwwroot` with an HTML redirect page instead of the
 * webservice reply. Every call to the Moodle webservice has to go through here.
 */
internal fun WebClient.Builder.forMoodle(credentials: Map<String, String>): WebClient.Builder {
    baseUrl(credentials["baseurl"] ?: "")
    credentials["publicurl"]?.takeIf { it.isNotBlank() }?.let(::buildHostHeader)?.let {
        defaultHeader(HttpHeaders.HOST, it)
    }
    return this
}

private fun buildHostHeader(url: String): String? {
    return try {
        val uri = URI(url)
        val host = uri.host ?: return null
        if (uri.port != -1) "$host:${uri.port}" else host
    } catch (_: Exception) {
        null
    }
}
