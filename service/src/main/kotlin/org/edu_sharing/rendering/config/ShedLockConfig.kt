package org.edu_sharing.rendering.config

import net.javacrumbs.shedlock.core.ExtensibleLockProvider
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider
import net.javacrumbs.shedlock.support.KeepAliveLockProvider
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicInteger

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
 *
 * The provider is wrapped in a [KeepAliveLockProvider]: while a task runs, its lock is extended every
 * `lockAtMostFor / 2`, so a task that outlives its `lockAtMostFor` (cache cleaner, stale-job reaper) keeps the
 * lock and no second pod starts in parallel. When the JVM dies the extension stops with it, so `lockAtMostFor`
 * still works as the crash protection. Every user of this bean profits (`@SchedulerLock` tasks and the
 * `RegistrationRunner`). Constraint of the wrapper: `lockAtMostFor` must be at least 30 s, or `lock()` throws.
 * The extension runs on its own small executor (not the shared task scheduler, whose threads may all be busy with
 * long tasks).
 */
@Configuration
class ShedLockConfig : DisposableBean {

    private val keepAliveExecutor = ScheduledThreadPoolExecutor(KEEP_ALIVE_THREADS) { runnable ->
        Thread(runnable, "shedlock-keepalive-${threadCounter.incrementAndGet()}").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    @Bean
    fun lockProvider(mongoDatabaseFactory: MongoDatabaseFactory): LockProvider =
        keepAlive(MongoLockProvider(mongoDatabaseFactory.mongoDatabase))

    internal fun keepAlive(provider: ExtensibleLockProvider): LockProvider =
        KeepAliveLockProvider(provider, keepAliveExecutor)

    override fun destroy() {
        keepAliveExecutor.shutdownNow()
    }

    private companion object {
        const val KEEP_ALIVE_THREADS = 2
        val threadCounter = AtomicInteger()
    }
}
