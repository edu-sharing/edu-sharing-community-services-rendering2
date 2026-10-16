package org.edu_sharing.rendering.edusharingRepo

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.SimpleLock
import org.edu_sharing.rendering.edusharingRepo.cors.CorsSyncService
import org.edu_sharing.rendering.edusharingRepo.cors.RegistrationCorsSyncRetrier
import org.edu_sharing.rendering.edusharingRepo.dto.RegisterRepositoryRequest
import org.edu_sharing.rendering.edusharingRepo.entity.ModuleSettings
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistrationConfig
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistrationConfigItem
import org.edu_sharing.rendering.edusharingRepo.services.PrivatePublicKeyService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.Optional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RegistrationRunnerTest {

    private val url = "http://repo.url/edu-sharing"
    private val keyService = mockk<PrivatePublicKeyService>(relaxed = true)
    private val registrationService = mockk<RepositoryRegistrationService>()
    private val corsSyncService = mockk<CorsSyncService>(relaxed = true)
    private val corsRetrier = mockk<RegistrationCorsSyncRetrier>(relaxed = true)
    private val lockProvider = mockk<LockProvider>()
    private val meterRegistry = SimpleMeterRegistry()
    private val config = RepositoryRegistrationConfig()

    private val storedRegistrations = mutableListOf<RepositoryRegistration>()
    private lateinit var underTest: RegistrationRunner

    private fun runner(timeoutSeconds: Long = 5) = RegistrationRunner(
        keyService, registrationService, config, corsSyncService, corsRetrier, lockProvider, meterRegistry,
        repoTimeoutSeconds = timeoutSeconds, retryInitialDelaySeconds = 0, retryMaxDelaySeconds = 0,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { lockProvider.lock(any()) } returns Optional.of(mockk<SimpleLock>(relaxed = true))
        every { keyService.hasKeyPair() } returns true
        every { registrationService.getRegisteredRepositories() } answers { storedRegistrations.toList() }
        configure(modules = listOf("MOODLE", "H5P"), settings = mapOf("H5P" to ModuleSettings(cspHeader = "default-src 'self'")))
        underTest = runner()
    }

    @AfterEach
    fun tearDown() = underTest.shutdown()

    private fun configure(modules: List<String>, settings: Map<String, ModuleSettings> = emptyMap()) {
        config.id = mapOf(
            "local" to RepositoryRegistrationConfigItem(
                url = url, username = "admin", password = "secret",
                optionalModules = modules, module = settings.toMutableMap()
            )
        )
    }

    private fun registered() = RepositoryRegistration(repoId = "repo1", url = url, publicKey = "k")

    @Test
    fun `application ready returns immediately even if the repository hangs`() {
        val hang = CountDownLatch(1)
        val entered = CountDownLatch(1)
        every { registrationService.registerWithRepository(any(), any(), any()) } answers {
            entered.countDown()
            hang.await()
            registered()
        }

        val start = System.nanoTime()
        assertDoesNotThrow { underTest.onApplicationReady() }
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        assertTrue(elapsedMs < 1000, "startup hook must not wait for the repository, took ${elapsedMs}ms")
        assertTrue(entered.await(5, TimeUnit.SECONDS), "registration should run in the background")
        hang.countDown()
    }

    @Test
    fun `hanging repository is cut off by the time budget and does not throw`() {
        underTest.shutdown()
        underTest = runner(timeoutSeconds = 1)
        every { registrationService.registerWithRepository(any(), any(), any()) } answers {
            Thread.sleep(30_000)
            registered()
        }

        val start = System.nanoTime()
        assertDoesNotThrow { underTest.reconcile(initial = true) }

        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 10)
        verify(exactly = 0) { registrationService.activateOptionalModule(any()) }
        assertEquals(1.0, meterRegistry.counter("rendering.registration.attempts", "result", "failure").count())
    }

    @Test
    fun `failing repository does not throw and retry job registers it once it is back`() {
        every { registrationService.registerWithRepository(any(), any(), any()) } throws IllegalStateException("repo down")

        assertDoesNotThrow { underTest.reconcile(initial = true) }
        verify(exactly = 0) { registrationService.activateOptionalModule(any()) }

        // Repository is back.
        every { registrationService.registerWithRepository(any(), true, true) } answers {
            registered().also { storedRegistrations += it }
        }
        every { registrationService.activateOptionalModule(any()) } answers {
            storedRegistrations.first().optionalModules.add(firstArg<org.edu_sharing.rendering.edusharingRepo.dto.ActivateOptionalModuleRequest>().module)
        }
        every { registrationService.setCspHeader(any(), any(), any()) } answers {
            storedRegistrations.first().module[secondArg()] = ModuleSettings(cspHeader = thirdArg())
        }

        underTest.reconcile(initial = false)

        // One failed attempt at startup plus the successful retry.
        verify(exactly = 2) { registrationService.registerWithRepository(match<RegisterRepositoryRequest> { it.url == url }, true, true) }
        verify(exactly = 1) { registrationService.activateOptionalModule(match { it.module == "MOODLE" }) }
        verify(exactly = 1) { registrationService.activateOptionalModule(match { it.module == "H5P" }) }
        verify(exactly = 1) { corsRetrier.syncWithRetry("repo1") }
        verify(exactly = 1) { corsSyncService.triggerSync() }

        // Everything is complete now: a further tick does nothing.
        underTest.reconcile(initial = false)
        verify(exactly = 2) { registrationService.registerWithRepository(any(), any(), any()) }
    }

    @Test
    fun `failed module is retried alone without re-registering the repository`() {
        val moodleCalls = java.util.concurrent.atomic.AtomicInteger()
        every { registrationService.registerWithRepository(any(), any(), any()) } answers {
            registered().also { storedRegistrations += it }
        }
        every { registrationService.activateOptionalModule(any()) } answers {
            val module = firstArg<org.edu_sharing.rendering.edusharingRepo.dto.ActivateOptionalModuleRequest>().module
            if (module == "MOODLE" && moodleCalls.incrementAndGet() == 1) throw IllegalArgumentException("Moodle unreachable")
            storedRegistrations.first().optionalModules.add(module)
        }
        every { registrationService.setCspHeader(any(), any(), any()) } answers {
            storedRegistrations.first().module[secondArg()] = ModuleSettings(cspHeader = thirdArg())
        }

        underTest.reconcile(initial = true)
        assertEquals(listOf("H5P"), storedRegistrations.first().optionalModules)

        underTest.reconcile(initial = false)

        assertEquals(setOf("H5P", "MOODLE"), storedRegistrations.first().optionalModules.toSet())
        verify(exactly = 1) { registrationService.registerWithRepository(any(), any(), any()) }
        assertEquals(2, moodleCalls.get())
    }

    @Test
    fun `csp failure of one module is contained and later retried`() {
        configure(modules = emptyList(), settings = mapOf(
            "A" to ModuleSettings(cspHeader = "a"), "B" to ModuleSettings(cspHeader = "b")
        ))
        every { registrationService.registerWithRepository(any(), any(), any()) } answers {
            registered().also { storedRegistrations += it }
        }
        var firstA = true
        every { registrationService.setCspHeader(any(), "A", any()) } answers {
            if (firstA) { firstA = false; throw IllegalStateException("db hiccup") }
            storedRegistrations.first().module["A"] = ModuleSettings(cspHeader = "a")
        }
        every { registrationService.setCspHeader(any(), "B", any()) } answers {
            storedRegistrations.first().module["B"] = ModuleSettings(cspHeader = "b")
        }

        assertDoesNotThrow { underTest.reconcile(initial = true) }
        verify(exactly = 1) { registrationService.setCspHeader("repo1", "B", "b") }

        underTest.reconcile(initial = false)
        verify(exactly = 2) { registrationService.setCspHeader("repo1", "A", "a") }
    }

    @Test
    fun `skips when another instance holds the lock`() {
        every { lockProvider.lock(any()) } returns Optional.empty()

        underTest.reconcile(initial = true)

        verify(exactly = 0) { registrationService.registerWithRepository(any(), any(), any()) }
    }

    @Test
    fun `pending gauge reflects incomplete repositories`() {
        every { registrationService.registerWithRepository(any(), any(), any()) } throws IllegalStateException("down")
        underTest.reconcile(initial = true)
        underTest.reconcile(initial = false)

        assertEquals(1.0, meterRegistry.get("rendering.registration.pending.repositories").gauge().value())
    }
}
