package org.edu_sharing.rendering.edusharingRepo.cors

import org.edu_sharing.generated.repository.backend.services.rest.client.ApiException
import org.edu_sharing.rendering.core.annotation.ConditionalOnMasterOrController
import org.slf4j.LoggerFactory
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component

/**
 * Transient failure during the startup CORS sync: the repository accepted the registration but
 * the freshly registered app has not yet replicated across the repository cluster, so the
 * app-scoped read (`applications1`) still fails. Retryable — it eventually succeeds once
 * replication catches up. Once the retry budget is exhausted this is rethrown as-is to the
 * caller ([org.edu_sharing.rendering.edusharingRepo.RegistrationRunner]), which logs it; it does
 * not affect application startup (registration runs asynchronously after the application is
 * ready) and the periodic CORS sync picks the repository up later.
 */
class RegistrationReplicationPendingException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Non-transient failure during the startup CORS sync (auth / misconfiguration). Not retried;
 * propagates to [org.edu_sharing.rendering.edusharingRepo.RegistrationRunner], which logs it and
 * leaves the repository to the periodic CORS sync. It does not fail application startup.
 */
class RegistrationSyncFatalException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Wraps the post-registration CORS sync in a bounded, exponentially backed-off retry so a
 * repository-cluster replication delay (the just-registered app not yet visible on the node
 * serving `applications1`) no longer aborts startup on the first attempt.
 *
 * Transient failures are retried for ~2 minutes (7 attempts: initial + 6 retries, delays
 * 5s/10s/20s/30s/30s/30s); if the budget is exhausted the last
 * [RegistrationReplicationPendingException] is rethrown to the runner (which logs it and carries
 * on). Genuine auth/misconfiguration is not retried and fails fast. The worst case (~2 minutes)
 * fits inside the runner's per-repository time budget and lock.
 *
 * Uses Spring Framework's native retry ([Retryable] + `@EnableResilientMethods`). It lives in
 * its own bean — not [CorsSyncService] (also used at runtime) nor the runner itself — because
 * the retry only engages through the AOP proxy on a cross-bean call.
 */
@Component
@ConditionalOnMasterOrController
class RegistrationCorsSyncRetrier(
    private val corsSyncService: CorsSyncService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Retryable(
        includes = [RegistrationReplicationPendingException::class],
        maxRetries = 6,
        delay = 5_000,
        multiplier = 2.0,
        maxDelay = 30_000,
    )
    fun syncWithRetry(repoId: String) {
        try {
            corsSyncService.syncAllowedOriginsWithRepository(repoId)
        } catch (e: ApiException) {
            throw classify(e, repoId)
        }
    }

    /**
     * Maps a generated-client [ApiException] to either a retryable or a fatal exception. The
     * client reports connection/IO errors with code 0 and HTTP errors with the response code
     * plus body (see its `ApiClient`).
     */
    private fun classify(e: ApiException, repoId: String): RuntimeException {
        val code = e.code
        val body = e.responseBody ?: ""
        val transient = code == 0 || // connection/IO: repository not reachable yet
            code >= 500 || // server-side transient error
            (code == 400 && body.contains("not found in the list of registered apps")) // replication lag
        return if (transient) {
            log.warn("Transient failure syncing CORS origins for repo $repoId (code=$code); will retry. body=$body")
            RegistrationReplicationPendingException(
                "Repository $repoId not ready for CORS sync yet (code=$code)", e
            )
        } else {
            log.error("Non-retryable failure syncing CORS origins for repo $repoId (code=$code). body=$body")
            RegistrationSyncFatalException(
                "CORS sync failed for repo $repoId with non-retryable status $code", e
            )
        }
    }
}
