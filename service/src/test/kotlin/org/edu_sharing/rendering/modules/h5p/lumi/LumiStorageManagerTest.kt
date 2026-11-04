package org.edu_sharing.rendering.modules.h5p.lumi

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiBucketInfo
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiLibraryCacheInfo
import org.edu_sharing.rendering.storage.StorageInfo
import org.edu_sharing.rendering.storage.StorageScopeKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional

@ExtendWith(MockKExtension::class)
class LumiStorageManagerTest {
    private val lumiContentManagementService = mockk<LumiContentManagementService>()

    private val repositoryRegistrationStorageService = mockk<RepositoryRegistrationStorageService>()

    private val underTest = LumiStorageManager(lumiContentManagementService, repositoryRegistrationStorageService)

    private fun registration(optionalModules: List<String>) = Optional.of(
        RepositoryRegistration(
            repoId = "repo1",
            url = "https://repo1.example.org",
            publicKey = "key",
            optionalModules = optionalModules.toMutableList()
        )
    )

    @BeforeEach
    fun h5pActive() {
        every { repositoryRegistrationStorageService.getRegistrationByRepoId("repo1") } returns
            registration(listOf(H5pRenderModule.MODULE_NAME))
    }

    @Test
    fun `reports the content bucket quota when lumi has one configured`() {
        every { lumiContentManagementService.getContentBucketInfo("repo1") } returns
            LumiBucketInfo(contentBucket = "lumi-contentbucket", contentBucketQuota = 5_000_000_000L)

        assertEquals(mapOf("lumi-contentbucket" to 5_000_000_000L), underTest.getManagedBucketQuotas("repo1"))
    }

    @Test
    fun `reports no quota when lumi has none configured`() {
        every { lumiContentManagementService.getContentBucketInfo("repo1") } returns
            LumiBucketInfo(contentBucket = "lumi-contentbucket", contentBucketQuota = null)

        assertEquals(emptyMap<String, Long>(), underTest.getManagedBucketQuotas("repo1"))
    }

    @Test
    fun `reports no quota instead of failing when lumi is unreachable`() {
        // Called on every admin dashboard poll and by the daily CacheCleaner — an unreachable lumi
        // must not break either, see LumiStorageManager.getManagedBucketQuotas.
        every { lumiContentManagementService.getContentBucketInfo("repo1") } throws RuntimeException("connection refused")

        assertEquals(emptyMap<String, Long>(), underTest.getManagedBucketQuotas("repo1"))
    }

    @Test
    fun `contributes a library cache scope when lumi reports one with a quota`() {
        every { lumiContentManagementService.getContentBucketInfo("repo1") } returns LumiBucketInfo(
            contentBucket = "lumi-contentbucket",
            libraryCache = LumiLibraryCacheInfo(usedBytes = 9_000, quota = 10_000)
        )

        assertEquals(
            listOf(
                StorageInfo(
                    repoId = "repo1",
                    bucket = "lumi-contentbucket",
                    size = 9_000,
                    maxSize = 10_000,
                    kind = StorageScopeKind.LIBRARY_CACHE
                )
            ),
            underTest.getAdditionalStorageInfo("repo1")
        )
    }

    @Test
    fun `contributes no library cache scope on the global library storage`() {
        // libraryCache is absent unless lumi runs the per-package cache.
        every { lumiContentManagementService.getContentBucketInfo("repo1") } returns
            LumiBucketInfo(contentBucket = "lumi-contentbucket", libraryCache = null)

        assertEquals(emptyList<StorageInfo>(), underTest.getAdditionalStorageInfo("repo1"))
    }

    @Test
    fun `contributes no library cache scope while no quota is configured`() {
        // Without a quota there is nothing to breathe against, so the volume is left alone.
        every { lumiContentManagementService.getContentBucketInfo("repo1") } returns LumiBucketInfo(
            contentBucket = "lumi-contentbucket",
            libraryCache = LumiLibraryCacheInfo(usedBytes = 9_000, quota = null)
        )

        assertEquals(emptyList<StorageInfo>(), underTest.getAdditionalStorageInfo("repo1"))
    }

    @Test
    fun `contributes no library cache scope instead of failing when lumi is unreachable`() {
        every { lumiContentManagementService.getContentBucketInfo("repo1") } throws RuntimeException("connection refused")

        assertEquals(emptyList<StorageInfo>(), underTest.getAdditionalStorageInfo("repo1"))
    }

    @Test
    fun `tolerates a library cache payload without a usedBytes field`() {
        // An absent field must not fail the whole lookup - it reads as zero usage.
        every { lumiContentManagementService.getContentBucketInfo("repo1") } returns LumiBucketInfo(
            contentBucket = "lumi-contentbucket",
            libraryCache = LumiLibraryCacheInfo(usedBytes = null, quota = 10_000)
        )

        assertEquals(0L, underTest.getAdditionalStorageInfo("repo1").single().size)
    }

    @Test
    fun `never consults lumi while H5P is not active for the repo`() {
        every { repositoryRegistrationStorageService.getRegistrationByRepoId("repo1") } returns registration(emptyList())

        assertEquals(emptyList<String>(), underTest.getManagedBuckets("repo1"))
        assertEquals(emptyMap<String, Long>(), underTest.getManagedBucketQuotas("repo1"))
        assertEquals(emptyList<StorageInfo>(), underTest.getAdditionalStorageInfo("repo1"))
        verify(exactly = 0) { lumiContentManagementService.getContentBucket(any()) }
        verify(exactly = 0) { lumiContentManagementService.getContentBucketInfo(any()) }
    }

    @Test
    fun `never consults lumi for a repo without registration`() {
        every { repositoryRegistrationStorageService.getRegistrationByRepoId("repo1") } returns Optional.empty()

        assertEquals(emptyList<String>(), underTest.getManagedBuckets("repo1"))
        assertEquals(emptyMap<String, Long>(), underTest.getManagedBucketQuotas("repo1"))
        assertEquals(emptyList<StorageInfo>(), underTest.getAdditionalStorageInfo("repo1"))
        verify(exactly = 0) { lumiContentManagementService.getContentBucket(any()) }
        verify(exactly = 0) { lumiContentManagementService.getContentBucketInfo(any()) }
    }

    @Test
    fun `still asks lumi for bucket ownership without H5P`() {
        every { repositoryRegistrationStorageService.getRegistrationByRepoId("repo1") } returns registration(emptyList())
        every { lumiContentManagementService.getContentBucket("repo1") } returns "lumi-contentbucket"

        assertEquals(true, underTest.isBucketOwner("lumi-contentbucket", "repo1"))
        verify(exactly = 1) { lumiContentManagementService.getContentBucket("repo1") }
    }
}
