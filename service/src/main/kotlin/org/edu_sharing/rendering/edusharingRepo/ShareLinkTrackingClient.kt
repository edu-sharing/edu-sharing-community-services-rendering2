package org.edu_sharing.rendering.edusharingRepo

import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException

/**
 * Reports interactions of recipients of a link share to the repository.
 *
 * Such a recipient has no account, so the usual tracking call (as the user, with an `EDU-TICKET` from
 * appAuth) is not possible - appAuth would create a repository account for the pseudo user of the share.
 * Instead the call is authorized by the app signature only, the repository attributes the event to the
 * link share.
 */
@Component
@ConditionalOnController
class ShareLinkTrackingClient(
    restClientProvider: RestClientProvider,
    private val authHeaderProvider: AuthHeaderProvider,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = restClientProvider.getSignedHttpClient()

    fun track(repositoryUrl: String, repoId: String, event: String, nodeId: String) {
        val url = "$repositoryUrl/rest/tracking/v1/tracking".toHttpUrl().newBuilder()
            .addPathSegment(repoId)
            .addPathSegment(event)
            .addPathSegment("share-link")
            .addQueryParameter("node", nodeId)
            .build()
        val request = Request.Builder()
            .url(url)
            .put(ByteArray(0).toRequestBody())
            .apply { authHeaderProvider.getAuthHeaders(repoId).forEach { (name, value) -> header(name, value) } }
            .build()
        log.debug("Tracking share link event={} for node={} in repo={}", event, nodeId, repoId)
        // fire and forget like the regular tracking, a failing report must not break the rendering
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                log.error("Failed to track share link event $event for object $nodeId in repo $repoId", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        log.debug("Successfully tracked share link event {} for object {} in repo {}", event, nodeId, repoId)
                    } else {
                        log.error("Tracking share link event {} for object {} in repo {} failed with status {}", event, nodeId, repoId, it.code)
                    }
                }
            }
        })
    }
}
