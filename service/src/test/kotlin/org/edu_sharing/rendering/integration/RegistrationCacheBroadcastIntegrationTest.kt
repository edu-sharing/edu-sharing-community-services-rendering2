package org.edu_sharing.rendering.integration

import org.edu_sharing.rendering.edusharingRepo.cache.RegistrationCacheConfig
import org.edu_sharing.rendering.edusharingRepo.cache.RegistrationChangedMessage
import org.edu_sharing.rendering.renderingJob.queue.QueueProperties
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager

/**
 * End to end over a real broker: a registration change announced by *another* instance reaches this pod's
 * anonymous listener queue (JSON payload, converter, container factory) and evicts the cached entry.
 */
class RegistrationCacheBroadcastIntegrationTest(
    @param:Autowired private val amqpTemplate: AmqpTemplate,
    @param:Autowired private val cacheManager: CacheManager,
    @param:Autowired private val queueProperties: QueueProperties,
) : AbstractIntegrationTest() {

    @Test
    fun broadcastFromAnotherInstanceEvictsTheLocalEntry() {
        val cache = cacheManager.getCache(RegistrationCacheConfig.REGISTRATIONS)!!
        cache.put("broadcast-repo", "stale")
        cacheManager.getCache(RegistrationCacheConfig.REPOSITORY_KEYS)!!.put("broadcast-repo", "stale")
        assertNotNull(cache.get("broadcast-repo"))

        amqpTemplate.convertAndSend(
            queueProperties.registrationBroadcastExchange,
            "",
            RegistrationChangedMessage(repoId = "broadcast-repo", origin = "another-instance")
        )

        val deadline = System.currentTimeMillis() + 10_000
        while (cache.get("broadcast-repo") != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        assertNull(cache.get("broadcast-repo"), "registrations entry was not evicted by the broadcast")
        assertNull(
            cacheManager.getCache(RegistrationCacheConfig.REPOSITORY_KEYS)!!.get("broadcast-repo"),
            "repositoryKeys entry was not evicted by the broadcast"
        )
    }
}
