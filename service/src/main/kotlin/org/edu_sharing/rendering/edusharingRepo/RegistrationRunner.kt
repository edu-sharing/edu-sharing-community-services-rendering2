package org.edu_sharing.rendering.edusharingRepo

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.LockingTaskExecutor
import org.edu_sharing.rendering.core.annotation.ConditionalOnMasterAndRegistration
import org.edu_sharing.rendering.edusharingRepo.cors.CorsSyncService
import org.edu_sharing.rendering.edusharingRepo.cors.RegistrationCorsSyncRetrier
import org.edu_sharing.rendering.edusharingRepo.dto.ActivateOptionalModuleRequest
import org.edu_sharing.rendering.edusharingRepo.dto.RegisterRepositoryRequest
import org.edu_sharing.rendering.edusharingRepo.entity.ModuleSettings
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistrationConfig
import org.edu_sharing.rendering.edusharingRepo.services.PrivatePublicKeyService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import jakarta.annotation.PreDestroy

/**
 * Registers the configured repositories (and activates their optional modules, CSP headers and CORS origins).
 *
 * Application startup never depends on a repository or a third-party module: the work starts only after
 * [ApplicationReadyEvent] on a virtual-thread executor, runs per repository in parallel and with a hard time
 * budget ([repoTimeoutSeconds]) per repository. Whatever fails (repository down, Moodle unreachable, ...) is
 * logged and retried by [retryPending] - a ShedLock-guarded scheduled job with exponential backoff - until it
 * succeeds. The state is derived from the persisted registrations, not remembered:
 * - repository not registered (no stored registration with the configured URL) -> full registration,
 * - configured optional module not active, or configured CSP header not stored -> only that part is repeated.
 * CORS origins are synced best-effort after a registration; the periodic CorsSyncScheduler heals a miss.
 *
 * The first pass keeps the previous startup semantics (`force = true`, `useInternal = true`); the key pair is
 * generated only once cluster-wide because all passes hold the same ShedLock lock. The lock lasts at most
 * budget + 60s, so it cannot outlive a crashed pod for long and cannot expire while a pass is still running.
 */
@Component
@Profile("!test")
@ConditionalOnMasterAndRegistration
class RegistrationRunner(
    private val privatePublicKeyService: PrivatePublicKeyService,
    private val repositoryRegistrationService: RepositoryRegistrationService,
    private val repositoryRegistrationConfig: RepositoryRegistrationConfig,
    private val corsSyncService: CorsSyncService,
    private val registrationCorsSyncRetrier: RegistrationCorsSyncRetrier,
    lockProvider: LockProvider,
    private val meterRegistry: MeterRegistry,
    @param:Value($$"${app.repository.registration.repo-timeout-seconds:240}")
    private val repoTimeoutSeconds: Long = 240,
    @param:Value($$"${app.repository.registration.retry-initial-delay-seconds:30}")
    private val retryInitialDelaySeconds: Long = 30,
    @param:Value($$"${app.repository.registration.retry-max-delay-seconds:600}")
    private val retryMaxDelaySeconds: Long = 600,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val lockingExecutor: LockingTaskExecutor = DefaultLockingTaskExecutor(lockProvider)
    private val executor: ExecutorService =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("registration-", 0).factory())

    private val running = AtomicBoolean(false)
    private val pendingRepos = AtomicInteger(0)
    private val backoff = ConcurrentHashMap<String, Backoff>()

    private data class Backoff(val failures: Int, val nextAttemptAt: Instant)

    private enum class Plan { FULL, INCREMENTAL }

    private class RepoItem(
        val request: RegisterRepositoryRequest,
        val optionalModules: List<String>,
        val moduleSettings: Map<String, ModuleSettings>,
    )

    init {
        Gauge.builder("rendering.registration.pending.repositories", pendingRepos) { it.get().toDouble() }
            .description("Configured repositories whose registration, module activation or CSP is not complete yet")
            .register(meterRegistry)
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    /** Initial pass; returns immediately - the work runs on the registration executor. */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() = dispatch(initial = true)

    /** Retries whatever is still incomplete, with per-repository backoff. */
    @Scheduled(
        fixedDelayString = $$"${app.repository.registration.retry-interval-millis:30000}",
        initialDelayString = $$"${app.repository.registration.retry-interval-millis:30000}",
    )
    fun retryPending() = dispatch(initial = false)

    private fun dispatch(initial: Boolean) {
        if (!running.compareAndSet(false, true)) {
            log.debug("Repository registration pass still running on this instance; skipping")
            return
        }
        try {
            executor.execute {
                try {
                    reconcile(initial)
                } catch (t: Throwable) {
                    log.error("Repository registration pass failed: ${t.javaClass.simpleName}: ${t.message}")
                } finally {
                    running.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            running.set(false)
        }
    }

    internal fun reconcile(initial: Boolean) {
        val items = repositoryRegistrationConfig.getAllRegistrations().map { RepoItem(it.first, it.second, it.third) }
        if (!initial) {
            val pending = items.filter { plan(it, initial = false) != null }
            pendingRepos.set(pending.size)
            val now = Instant.now()
            if (pending.none { backoff[it.request.url]?.nextAttemptAt?.isAfter(now) != true }) {
                return
            }
        }

        // lockAtMostFor is coupled to the pass budget: parallel repositories, each bounded by the budget.
        val budget = Duration.ofSeconds(repoTimeoutSeconds)
        val lockConfiguration = LockConfiguration(
            Instant.now(), "repositoryRegistration", budget.plusSeconds(60), Duration.ZERO,
        )
        val result = lockingExecutor.executeWithLock(
            LockingTaskExecutor.TaskWithResult { runPass(items, initial, budget) },
            lockConfiguration,
        )
        if (!result.wasExecuted()) {
            log.info("Repository registration skipped - another master instance holds the lock")
        }
    }

    private fun runPass(items: List<RepoItem>, initial: Boolean, budget: Duration) {
        // Re-evaluated under the lock: another pod may have completed the work in the meantime.
        val now = Instant.now()
        val work = items.mapNotNull { item ->
            val plan = plan(item, initial) ?: return@mapNotNull null
            if (!initial && backoff[item.request.url]?.nextAttemptAt?.isAfter(now) == true) return@mapNotNull null
            item to plan
        }
        if (work.isEmpty()) {
            pendingRepos.set(0)
            return
        }

        try {
            ensureKeyPair()
        } catch (e: Exception) {
            log.error("Could not ensure the application key pair: ${e.javaClass.simpleName}: ${e.message}")
            work.forEach { recordFailure(it.first, "key pair unavailable") }
            return
        }

        val deadline = System.nanoTime() + budget.toNanos()
        val futures: List<Triple<RepoItem, Plan, Future<RepositoryRegistration?>>> = work.map { (item, plan) ->
            Triple(item, plan, executor.submit<RepositoryRegistration?> { processRepo(item, plan) })
        }
        var newRegistrations = 0
        futures.forEach { (item, plan, future) ->
            try {
                val remaining = (deadline - System.nanoTime()).coerceAtLeast(0)
                val failed = future.get(remaining, TimeUnit.NANOSECONDS) == null
                if (failed) recordFailure(item, "incomplete") else {
                    recordSuccess(item)
                    if (plan == Plan.FULL) newRegistrations++
                }
            } catch (_: TimeoutException) {
                future.cancel(true)
                recordFailure(item, "exceeded the time budget of ${budget.seconds}s")
            } catch (e: ExecutionException) {
                recordFailure(item, "${e.cause?.javaClass?.simpleName}: ${e.cause?.message}")
            } catch (_: CancellationException) {
                recordFailure(item, "cancelled")
            }
        }
        pendingRepos.set(items.count { plan(it, initial = false) != null })
        if (newRegistrations > 0) {
            try {
                corsSyncService.triggerSync()
            } catch (e: Exception) {
                log.warn("Could not trigger CORS sync broadcast: ${e.message}")
            }
        }
    }

    private fun ensureKeyPair() {
        log.debug("Checking application key pair")
        if (!privatePublicKeyService.hasKeyPair()) {
            log.debug("No existing key pair found; generating new key pair")
            privatePublicKeyService.generateApplicationKeyPair()
        }
    }

    /** Derives what is still to do for a repository from the persisted registrations; null = complete. */
    private fun plan(item: RepoItem, initial: Boolean): Plan? {
        if (initial) return Plan.FULL
        return try {
            val registration = findRegistration(item) ?: return Plan.FULL
            if (missingModules(item, registration).isNotEmpty() || cspToApply(item, registration).isNotEmpty()) {
                Plan.INCREMENTAL
            } else {
                null
            }
        } catch (e: Exception) {
            log.warn("Could not determine registration state for ${item.request.url}: ${e.message}")
            null
        }
    }

    private fun findRegistration(item: RepoItem) =
        repositoryRegistrationService.getRegisteredRepositories().firstOrNull { it.url == item.request.url }

    private fun missingModules(item: RepoItem, registration: RepositoryRegistration) =
        item.optionalModules.filterNot { registration.optionalModules.contains(it) }

    private fun cspToApply(item: RepoItem, registration: RepositoryRegistration) =
        item.moduleSettings.filter { (module, settings) -> registration.module[module]?.cspHeader != settings.cspHeader }

    /** @return the registration if everything succeeded, null if some part failed (logged). */
    private fun processRepo(item: RepoItem, plan: Plan): RepositoryRegistration? {
        val url = item.request.url
        var complete = true
        try {
            var registration: RepositoryRegistration
            var modules = item.optionalModules
            var csp = item.moduleSettings
            var registered = false
            val existing = if (plan == Plan.INCREMENTAL) findRegistration(item) else null
            if (existing != null) {
                registration = existing
                modules = missingModules(item, existing)
                csp = cspToApply(item, existing)
            } else {
                log.debug("Processing auto-registration for $url with ${item.optionalModules.size} optional modules")
                registration = repositoryRegistrationService.registerWithRepository(
                    request = item.request, force = true, useInternal = true
                )
                registered = true
            }
            modules.forEach { module ->
                // Best-effort per module: a broken third-party endpoint (e.g. Moodle returning an error
                // payload) only leaves that one module inactive; the retry job picks it up later.
                try {
                    repositoryRegistrationService.activateOptionalModule(
                        ActivateOptionalModuleRequest(
                            repoId = registration.repoId,
                            module = module,
                            credentials = item.moduleSettings[module]?.credentials
                        )
                    )
                    log.info("Optional module activated: $module.")
                } catch (e: Exception) {
                    complete = false
                    log.error("Failed to activate optional module $module for $url (will retry): ${e.message}")
                }
            }
            // Re-applied after activation: activating a third-party module replaces its stored settings.
            val cspTargets = if (registered || modules.isNotEmpty()) item.moduleSettings else csp
            cspTargets.forEach { (module, settings) ->
                try {
                    repositoryRegistrationService.setCspHeader(
                        repoId = registration.repoId, module = module, cspHeader = settings.cspHeader
                    )
                } catch (e: Exception) {
                    complete = false
                    log.error("Failed to set CSP header of module $module for $url (will retry): ${e.message}")
                }
            }
            val orphanedSettingsKeys = item.moduleSettings.keys.subtract(item.optionalModules.toSet())
            if (registered && orphanedSettingsKeys.isNotEmpty()) {
                log.warn("Settings provided for modules: ${orphanedSettingsKeys.joinToString(",")}. These modules are not in the optional-modules list. Did you forget them?")
            }
            if (registered) {
                log.info("Registration completed for $url.")
                try {
                    registrationCorsSyncRetrier.syncWithRetry(registration.repoId)
                    log.info("Synced allowed origins for ${registration.repoId}.")
                } catch (e: Exception) {
                    // Not part of the retry state: the periodic CORS sync repeats it.
                    log.warn("Initial CORS sync for ${registration.repoId} failed (periodic sync will retry): ${e.message}")
                }
            }
            return if (complete) registration else null
        } catch (e: Exception) {
            log.error("Error while registering $url: ${e.javaClass.simpleName}: ${e.message}")
            log.debug("Registration failure details for $url", e)
            return null
        }
    }

    private fun recordSuccess(item: RepoItem) {
        backoff.remove(item.request.url)
        Counter.builder("rendering.registration.attempts").tag("result", "success").register(meterRegistry).increment()
    }

    private fun recordFailure(item: RepoItem, reason: String) {
        val failures = (backoff[item.request.url]?.failures ?: 0) + 1
        val delay = (retryInitialDelaySeconds shl (failures - 1).coerceAtMost(16)).coerceIn(0, retryMaxDelaySeconds)
        backoff[item.request.url] = Backoff(failures, Instant.now().plusSeconds(delay))
        Counter.builder("rendering.registration.attempts").tag("result", "failure").register(meterRegistry).increment()
        log.warn("Repository ${item.request.url} not fully registered ($reason); attempt $failures, retrying in ${delay}s")
    }
}
