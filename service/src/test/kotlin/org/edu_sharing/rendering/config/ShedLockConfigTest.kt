package org.edu_sharing.rendering.config

import net.javacrumbs.shedlock.core.AbstractSimpleLock
import net.javacrumbs.shedlock.core.ExtensibleLockProvider
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.SimpleLock
import net.javacrumbs.shedlock.support.keepAliveProviderWithShortMinimum
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class ShedLockConfigTest {

    /** In-memory provider with Mongo-like semantics: a lock is free once its lockUntil has passed. */
    private class InMemoryLockProvider : ExtensibleLockProvider {
        val lockUntil = ConcurrentHashMap<String, Instant>()

        @Synchronized
        override fun lock(configuration: LockConfiguration): Optional<SimpleLock> {
            val until = lockUntil[configuration.name]
            if (until != null && until.isAfter(Instant.now())) return Optional.empty()
            lockUntil[configuration.name] = configuration.lockAtMostUntil
            return Optional.of(MemoryLock(configuration))
        }

        private inner class MemoryLock(configuration: LockConfiguration) : AbstractSimpleLock(configuration) {
            @Synchronized
            override fun doUnlock() {
                // like ShedLock: keep the lock until lockAtLeastUntil, never beyond the current lockUntil
                lockUntil[lockConfiguration.name] = minOf(Instant.now(), lockUntil[lockConfiguration.name]!!)
                    .let { maxOf(it, lockConfiguration.lockAtLeastUntil) }
            }

            @Synchronized
            override fun doExtend(newConfiguration: LockConfiguration): Optional<SimpleLock> {
                val until = lockUntil[newConfiguration.name]
                if (until == null || until.isBefore(Instant.now())) return Optional.empty()
                lockUntil[newConfiguration.name] = newConfiguration.lockAtMostUntil
                return Optional.of(MemoryLock(newConfiguration))
            }
        }
    }

    private val executor = Executors.newScheduledThreadPool(1)

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    private fun configuration(atMostFor: Duration) =
        LockConfiguration(Instant.now(), "task", atMostFor, Duration.ZERO)

    @Test
    fun aTaskRunningLongerThanLockAtMostForKeepsTheLockUntilItEnds() {
        val provider = keepAliveProviderWithShortMinimum(InMemoryLockProvider(), executor, Duration.ofMillis(100))
        val lock = provider.lock(configuration(Duration.ofSeconds(1))).orElseThrow()

        // the task runs for 2.5 s, 2.5x its lockAtMostFor
        Thread.sleep(2500)
        assertTrue(provider.lock(configuration(Duration.ofSeconds(1))).isEmpty, "second pod must not get the lock")

        lock.unlock()
        val second = provider.lock(configuration(Duration.ofSeconds(1)))
        assertTrue(second.isPresent, "the lock must be released after the task")
        second.get().unlock()
    }

    @Test
    fun aCrashedHolderLosesTheLockAfterLockAtMostFor() {
        val memory = InMemoryLockProvider()
        val provider = keepAliveProviderWithShortMinimum(memory, executor, Duration.ofMillis(100))
        provider.lock(configuration(Duration.ofSeconds(1))).orElseThrow()

        // simulate a dying JVM: no more extensions
        executor.shutdownNow()
        Thread.sleep(1200)

        assertTrue(memory.lock(configuration(Duration.ofSeconds(1))).isPresent)
    }

    @Test
    fun theConfiguredProviderRejectsALockAtMostForBelowTheMinimum() {
        val config = ShedLockConfig()
        try {
            val provider = config.keepAlive(InMemoryLockProvider())
            assertThrows<IllegalArgumentException> { provider.lock(configuration(Duration.ofSeconds(10))) }
            val lock = provider.lock(configuration(Duration.ofSeconds(60)))
            assertFalse(lock.isEmpty)
            assertEquals(true, lock.isPresent)
            lock.get().unlock()
        } finally {
            config.destroy()
        }
    }
}
