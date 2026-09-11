package org.edu_sharing.rendering.storage

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.cacheCleaner.BucketAggregation
import org.edu_sharing.rendering.cacheCleaner.BucketInfo
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.storage.bucket.BucketStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.awssdk.services.s3.S3Client
import java.util.Optional

/**
 * Tests only [S3StorageService.getStorageInfo] — how the CacheCleaner scopes are built from the
 * tracking aggregation and the bucket quotas reported via [StorageManagerRegistry] (strictly
 * per-bucket as soon as any bucket quota is known; otherwise the pre-existing repo-wide scope).
 */
@ExtendWith(MockKExtension::class)
class S3StorageServiceTest {
    private val s3Client = mockk<S3Client>()
    private val trackingService = mockk<TrackingService>()
    private val appInfo = mockk<AppInfo>()
    private val bucketStrategy = mockk<BucketStrategy>()
    private val repoRegistrationStorageService = mockk<RepositoryRegistrationStorageService>()
    private val storageManagerRegistry = mockk<StorageManagerRegistry>()

    private val underTest = S3StorageService(
        s3Client, trackingService, appInfo, bucketStrategy, repoRegistrationStorageService, storageManagerRegistry
    )

    private fun registration(quota: Long) = Optional.of(
        RepositoryRegistration(repoId = "repo1", url = "https://repo1.example.org", publicKey = "key", quota = quota)
    )

    @Test
    fun `repo without any bucket quota falls back to the repo-wide scope`() {
        every { trackingService.getBucketAggregation() } returns listOf(
            BucketAggregation(repoId = "repo1", buckets = listOf(BucketInfo("rs2-image", 300), BucketInfo("rs2-video", 200)), totalSize = 500)
        )
        every { storageManagerRegistry.getManagedBucketQuotas("repo1") } returns emptyMap()
        every { repoRegistrationStorageService.getRegistrationByRepoId("repo1") } returns registration(1000)

        val result = underTest.getStorageInfo()

        assertEquals(listOf(StorageInfo(repoId = "repo1", bucket = null, size = 500, maxSize = 1000)), result)
    }

    @Test
    fun `a single bucket quota switches the repo to strict per-bucket scopes`() {
        every { trackingService.getBucketAggregation() } returns listOf(
            BucketAggregation(
                repoId = "repo1",
                buckets = listOf(BucketInfo("rendering2", 300), BucketInfo("lumi-contentbucket", 50)),
                totalSize = 350
            )
        )
        // Only the renderingBucket has a quota — the lumi bucket reports none (e.g. lumi is
        // unreachable, or has no quota configured).
        every { storageManagerRegistry.getManagedBucketQuotas("repo1") } returns mapOf("rendering2" to 1000L)

        val result = underTest.getStorageInfo()

        // The repo-wide quota (even if it were queried at all) must play no role here — the
        // repoRegistrationStorageService isn't even consulted for the quota anymore.
        assertEquals(listOf(StorageInfo(repoId = "repo1", bucket = "rendering2", size = 300, maxSize = 1000)), result)
    }

    @Test
    fun `bucket with a quota but no tracked entries yet reports zero size`() {
        every { trackingService.getBucketAggregation() } returns listOf(
            BucketAggregation(repoId = "repo1", buckets = emptyList(), totalSize = 0)
        )
        every { storageManagerRegistry.getManagedBucketQuotas("repo1") } returns mapOf("rendering2" to 1000L)

        val result = underTest.getStorageInfo()

        assertEquals(listOf(StorageInfo(repoId = "repo1", bucket = "rendering2", size = 0, maxSize = 1000)), result)
    }

    @Test
    fun `multiple repos are resolved independently`() {
        every { trackingService.getBucketAggregation() } returns listOf(
            BucketAggregation(repoId = "repo1", buckets = listOf(BucketInfo("rs2-image", 100)), totalSize = 100),
            BucketAggregation(repoId = "repo2", buckets = listOf(BucketInfo("rendering2", 200)), totalSize = 200),
        )
        every { storageManagerRegistry.getManagedBucketQuotas("repo1") } returns emptyMap()
        every { storageManagerRegistry.getManagedBucketQuotas("repo2") } returns mapOf("rendering2" to 500L)
        every { repoRegistrationStorageService.getRegistrationByRepoId("repo1") } returns registration(300)

        val result = underTest.getStorageInfo()

        assertEquals(
            listOf(
                StorageInfo(repoId = "repo1", bucket = null, size = 100, maxSize = 300),
                StorageInfo(repoId = "repo2", bucket = "rendering2", size = 200, maxSize = 500),
            ),
            result,
        )
    }
}
