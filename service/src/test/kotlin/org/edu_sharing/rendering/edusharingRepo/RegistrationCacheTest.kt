package org.edu_sharing.rendering.edusharingRepo

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.rendering.edusharingRepo.cache.RegistrationCacheBroadcaster
import org.edu_sharing.rendering.edusharingRepo.cache.RegistrationCacheConfig
import org.edu_sharing.rendering.edusharingRepo.cache.RegistrationCacheProperties
import org.edu_sharing.rendering.edusharingRepo.cache.RegistrationCacheReceiver
import org.edu_sharing.rendering.edusharingRepo.cache.RegistrationChangedMessage
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistrationConfig
import org.edu_sharing.rendering.edusharingRepo.repository.RepositoryRegistrationRepository
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.util.Optional

/**
 * The registration cache must (1) really engage through the proxy, so a second read does not hit the database,
 * (2) not remember a *missing* registration, (3) be refreshed by a write on this pod and (4) be evicted by a
 * broadcast from another pod - but not by this pod's own.
 */
@SpringJUnitConfig
class RegistrationCacheTest {

    @Configuration
    @Import(RegistrationCacheConfig::class, RegistrationCacheProperties::class)
    class Config {
        @Bean
        fun repository(): RepositoryRegistrationRepository = mockk(relaxed = true)

        @Bean
        fun broadcaster(): RegistrationCacheBroadcaster = mockk<RegistrationCacheBroadcaster>(relaxed = true).also {
            every { it.instanceId } returns "me"
        }

        @Bean
        fun storage(
            repository: RepositoryRegistrationRepository,
            broadcaster: RegistrationCacheBroadcaster
        ) = RepositoryRegistrationStorageService(repository, RepositoryRegistrationConfig(), broadcaster, true)

        @Bean
        fun receiver(cacheManager: org.springframework.cache.CacheManager, broadcaster: RegistrationCacheBroadcaster) =
            RegistrationCacheReceiver(cacheManager, broadcaster)
    }

    @Autowired lateinit var storage: RepositoryRegistrationStorageService
    @Autowired lateinit var repository: RepositoryRegistrationRepository
    @Autowired lateinit var broadcaster: RegistrationCacheBroadcaster
    @Autowired lateinit var receiver: RegistrationCacheReceiver
    @Autowired lateinit var cacheManager: org.springframework.cache.CacheManager

    private fun registration(url: String = "http://repo") =
        RepositoryRegistration(repoId = "repo1", url = url, publicKey = "key")

    @BeforeEach
    fun reset() {
        cacheManager.cacheNames.forEach { cacheManager.getCache(it)?.clear() }
        clearMocks(repository, answers = false)
        clearMocks(broadcaster, answers = false)
        every { broadcaster.instanceId } returns "me"
    }

    @Test
    fun `second read is served from the cache`() {
        every { repository.findByRepoId("repo1") } returns Optional.of(registration())

        storage.getRegistrationByRepoId("repo1")
        storage.getRegistrationByRepoId("repo1")

        verify(exactly = 1) { repository.findByRepoId("repo1") }
    }

    @Test
    fun `a missing registration is not cached`() {
        every { repository.findByRepoId("repo1") } returns Optional.empty()
        assertTrue(storage.getRegistrationByRepoId("repo1").isEmpty)

        every { repository.findByRepoId("repo1") } returns Optional.of(registration())
        assertTrue(storage.getRegistrationByRepoId("repo1").isPresent)
    }

    @Test
    fun `fresh read bypasses the cache`() {
        every { repository.findByRepoId("repo1") } returns Optional.of(registration())

        storage.getRegistrationByRepoId("repo1")
        storage.getRegistrationByRepoIdFresh("repo1")

        verify(exactly = 2) { repository.findByRepoId("repo1") }
    }

    @Test
    fun `storing refreshes the cache and broadcasts`() {
        val updated = registration(url = "http://updated")
        every { repository.findByRepoId("repo1") } returns Optional.of(registration())
        every { repository.save(updated) } returns updated

        storage.getRegistrationByRepoId("repo1")
        storage.storeRegistration(updated)

        assertEquals("http://updated", storage.getRegistrationByRepoId("repo1").get().url)
        verify(exactly = 1) { repository.findByRepoId("repo1") }
        verify(exactly = 1) { broadcaster.registrationChanged("repo1") }
    }

    @Test
    fun `removing evicts the cache and broadcasts`() {
        every { repository.findByRepoId("repo1") } returns Optional.of(registration())
        every { repository.removeByRepoId("repo1") } returns Optional.of(registration())

        storage.getRegistrationByRepoId("repo1")
        storage.removeRegistration("repo1")
        every { repository.findByRepoId("repo1") } returns Optional.empty()

        assertTrue(storage.getRegistrationByRepoId("repo1").isEmpty)
        verify(exactly = 1) { broadcaster.registrationChanged("repo1") }
    }

    @Test
    fun `a broadcast from another instance evicts, the own one does not`() {
        every { repository.findByRepoId("repo1") } returns Optional.of(registration())
        storage.getRegistrationByRepoId("repo1")

        receiver.handle(RegistrationChangedMessage(repoId = "repo1", origin = "me"))
        storage.getRegistrationByRepoId("repo1")
        verify(exactly = 1) { repository.findByRepoId("repo1") }

        receiver.handle(RegistrationChangedMessage(repoId = "repo1", origin = "someone-else"))
        storage.getRegistrationByRepoId("repo1")
        verify(exactly = 2) { repository.findByRepoId("repo1") }
    }
}
