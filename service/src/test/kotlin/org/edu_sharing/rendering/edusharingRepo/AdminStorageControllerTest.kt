package org.edu_sharing.rendering.edusharingRepo

import io.mockk.every
import io.mockk.mockk
import org.edu_sharing.rendering.cacheCleaner.BucketAggregation
import org.edu_sharing.rendering.cacheCleaner.BucketInfo
import org.edu_sharing.rendering.cacheCleaner.CacheCleaner
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.storage.StorageInfo
import org.edu_sharing.rendering.storage.StorageManager
import org.edu_sharing.rendering.storage.StorageManagerRegistry
import org.edu_sharing.rendering.storage.StorageScopeKind
import org.edu_sharing.rendering.storage.StorageService
import org.edu_sharing.rendering.storage.bucket.BucketStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional

/**
 * The storage overview of the admin dashboard. lumi's H5P library cache is a volume, not a bucket, so it
 * is in none of the tracked buckets - these tests pin that it still gets a row of its own.
 */
class AdminStorageControllerTest {
    private val repoId = "repo1"

    private val trackingService = mockk<TrackingService>()
    private val registrations = mockk<RepositoryRegistrationStorageService>()
    private val lumi = mockk<StorageManager>()

    private fun controller(vararg libraryCache: StorageInfo): AdminStorageController {
        every { registrations.getRegistrationByRepoId(repoId) } returns
            Optional.of(RepositoryRegistration(repoId = repoId, url = "http://repo", publicKey = "key", quota = 1_000))
        every { trackingService.getBucketAggregation() } returns listOf(
            BucketAggregation(repoId, listOf(BucketInfo("lumi-contentbucket", 400), BucketInfo("rendering2", 100)), 500)
        )
        every { lumi.getManagedBucketQuotas(repoId) } returns mapOf("lumi-contentbucket" to 800L)
        every { lumi.getAdditionalStorageInfo(repoId) } returns libraryCache.toList()
        return AdminStorageController(
            trackingService, mockk<StorageService>(), mockk<BucketStrategy>(), registrations,
            StorageManagerRegistry(listOf(lumi)), mockk<CacheCleaner>()
        )
    }

    private fun libraryCache(size: Long, quota: Long) =
        StorageInfo(repoId, "lumi-contentbucket", size, quota, StorageScopeKind.LIBRARY_CACHE)

    @Test
    fun `the library cache gets a row of its own with the figures the cache cleaner acts on`() {
        val usage = controller(libraryCache(size = 60, quota = 100)).getStorageUsage(repoId, exact = false)

        val row = usage.buckets.single { it.kind == StorageScopeKind.LIBRARY_CACHE }
        assertEquals(60, row.size)
        assertEquals(100, row.quota)
        assertEquals(60.0, row.usedPercent)
        assertTrue(row.enforced, "the cleaner enforces it")
        assertTrue(row.measured)
    }

    @Test
    fun `the buckets stay buckets`() {
        val usage = controller(libraryCache(60, 100)).getStorageUsage(repoId, exact = false)

        assertEquals(
            setOf("lumi-contentbucket", "rendering2"),
            usage.buckets.filter { it.kind == StorageScopeKind.BUCKET }.map { it.name }.toSet()
        )
    }

    @Test
    fun `the library cache is a volume and not part of the repository total`() {
        val usage = controller(libraryCache(size = 60, quota = 100)).getStorageUsage(repoId, exact = false)

        assertEquals(500, usage.totalSize)
        assertEquals(50.0, usage.usedPercent)
    }

    @Test
    fun `a cache over its quota shows more than 100 percent`() {
        val usage = controller(libraryCache(size = 130, quota = 100)).getStorageUsage(repoId, exact = false)

        assertEquals(130.0, usage.buckets.single { it.kind == StorageScopeKind.LIBRARY_CACHE }.usedPercent)
    }

    @Test
    fun `there is no row when lumi reports no library cache`() {
        // global library storage, no quota, or a size that is not known yet
        val usage = controller().getStorageUsage(repoId, exact = false)

        assertTrue(usage.buckets.none { it.kind == StorageScopeKind.LIBRARY_CACHE })
        assertNotNull(usage.buckets.singleOrNull { it.name == "lumi-contentbucket" })
    }

    @Test
    fun `other additional scopes are not mistaken for the library cache`() {
        val bucketScope = StorageInfo(repoId, "x", 1, 2, StorageScopeKind.BUCKET)

        val usage = controller(bucketScope).getStorageUsage(repoId, exact = false)

        assertTrue(usage.buckets.none { it.kind == StorageScopeKind.LIBRARY_CACHE })
    }
}
