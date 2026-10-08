package org.edu_sharing.rendering.edusharingRepo.cache

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Enables the `@Cacheable` annotations on the registration/key services with a **bounded, expiring** Caffeine
 * cache (Spring's default would be an unbounded map). Pod-local; changes are propagated by
 * [RegistrationCacheBroadcaster] / [RegistrationCacheReceiver].
 */
@Configuration
@EnableCaching
class RegistrationCacheConfig {

    companion object {
        /** `RepositoryRegistration` by repoId. */
        const val REGISTRATIONS = "registrations"

        /** Parsed repository `PublicKey` by repoId. */
        const val REPOSITORY_KEYS = "repositoryKeys"

        /** The rendering service's own private key. */
        const val PRIVATE_KEY = "privateKey"
    }

    @Bean
    fun cacheManager(properties: RegistrationCacheProperties): CacheManager {
        val manager = CaffeineCacheManager(REGISTRATIONS, REPOSITORY_KEYS, PRIVATE_KEY)
        manager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(properties.maximumSize)
                .expireAfterWrite(properties.expireAfterWrite)
        )
        return manager
    }
}
