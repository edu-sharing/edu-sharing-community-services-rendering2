package org.edu_sharing.rendering.storage.bucket

import org.edu_sharing.rendering.edusharingRepo.entity.ExternalBucket
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistrationConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Warns at startup when two configured external bucket scopes (bucket name + key prefix) overlap, e.g. the rendering
 * and the temp bucket pointing to the same bucket without distinct prefixes. Their objects would then mix, so size
 * measurements and prefix deletes cover both. This is only a warning, not a failure: installs that predate key
 * prefixes may legitimately run rendering and temp in one bucket.
 */
@Component
@ConditionalOnExternalBucket
class ExternalBucketScopeValidator(
    private val config: RepositoryRegistrationConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** One configured bucket scope; [prefix] is normalized (see [ExternalBucket.normalizeKeyPrefix]). */
    data class Scope(val label: String, val bucket: String, val prefix: String)

    @EventListener(ApplicationReadyEvent::class)
    fun validate() {
        val scopes = config.id.flatMap { (key, item) ->
            listOfNotNull(
                item.externalBuckets?.renderingBucket?.let { "$key.renderingBucket" to it },
                item.externalBuckets?.tempBucket?.let { "$key.tempBucket" to it }
            )
        }
            .filter { (_, bucket) -> bucket.name.isNotBlank() }
            .map { (label, bucket) -> Scope(label, bucket.name, ExternalBucket.normalizeKeyPrefix(bucket.keyPrefix)) }

        findOverlaps(scopes).forEach { (a, b) ->
            log.warn(
                "External bucket scopes '{}' and '{}' overlap in bucket '{}' (key prefixes {} and {}): their objects " +
                    "mix, so size measurements and prefix deletes cover both. Configure distinct, non-nested key " +
                    "prefixes (RENDERING2_S3_RENDERING_BUCKET_KEY_PREFIX / RENDERING2_S3_TEMP_BUCKET_KEY_PREFIX, " +
                    "Helm buckets[].keyPrefix).",
                a.label, b.label, a.bucket, describe(a.prefix), describe(b.prefix)
            )
        }
    }

    private fun describe(prefix: String) = if (prefix.isEmpty()) "(bucket root)" else "'$prefix'"

    companion object {
        /**
         * All pairs of [scopes] in the same bucket where one normalized prefix starts with the other. The empty
         * prefix (bucket root) covers everything; the trailing slash keeps `a/` and `ab/` apart.
         */
        fun findOverlaps(scopes: List<Scope>): List<Pair<Scope, Scope>> =
            scopes.flatMapIndexed { i, a ->
                scopes.drop(i + 1)
                    .filter { b -> a.bucket == b.bucket && (a.prefix.startsWith(b.prefix) || b.prefix.startsWith(a.prefix)) }
                    .map { b -> a to b }
            }
    }
}
