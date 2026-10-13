package org.edu_sharing.rendering.edusharingRepo.cache

import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component

/**
 * Evicts the local copy of a registration another pod just changed. Runs on every role. Each pod listens on its
 * own anonymous (exclusive, auto-delete) queue bound to the fanout exchange, so every pod gets every message.
 */
@Component
class RegistrationCacheReceiver(
    private val cacheManager: CacheManager,
    private val broadcaster: RegistrationCacheBroadcaster
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "", durable = "false", exclusive = "true", autoDelete = "true"),
                exchange = Exchange(
                    name = "#{queueProperties.registrationBroadcastExchange}",
                    type = "fanout"
                )
            )
        ],
        containerFactory = "queueListenerContainerFactory"
    )
    fun handle(message: RegistrationChangedMessage) {
        if (message.origin == broadcaster.instanceId) return
        log.debug("Registration {} changed on another instance; evicting local cache entries", message.repoId)
        evict(message.repoId)
    }

    fun evict(repoId: String) {
        cacheManager.getCache(RegistrationCacheConfig.REGISTRATIONS)?.evict(repoId)
        cacheManager.getCache(RegistrationCacheConfig.REPOSITORY_KEYS)?.evict(repoId)
    }
}
