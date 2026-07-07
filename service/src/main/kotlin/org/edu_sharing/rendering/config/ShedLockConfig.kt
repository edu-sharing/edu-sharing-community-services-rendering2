package org.edu_sharing.rendering.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory

/**
 * ShedLock provider backed by MongoDB. Used to guard master-only work (scheduled cleanup,
 * CORS sync, startup registration) so it runs at most once cluster-wide even if more than
 * one master instance is temporarily active.
 *
 * `MongoDatabaseFactory.mongoDatabase` returns the synchronous `com.mongodb.client.MongoDatabase`
 * expected by [MongoLockProvider], and reuses the configured database name / credentials from
 * Spring Data. The lock lives in the `shedLock` collection (one document per lock name); expiry
 * is handled logically via each document's `lockUntil` field (no TTL index), which makes it
 * deadlock-free: a crashed lock holder's lock is considered free once `lockUntil` has passed.
 */
@Configuration
class ShedLockConfig {

    @Bean
    fun lockProvider(mongoDatabaseFactory: MongoDatabaseFactory): LockProvider =
        MongoLockProvider(mongoDatabaseFactory.mongoDatabase)
}
