package org.edu_sharing.rendering.cacheCleaner

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.edu_sharing.rendering.storage.StorageManager
import org.edu_sharing.rendering.storage.StorageManagerRegistry
import org.edu_sharing.rendering.storage.StorageInfo
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.util.Date
import java.util.function.Function

/**
 * Unit tests for the CacheCleaner deletion path: which LRU scope (whole repo vs. a single bucket)
 * yields which tracking entries, how many of them actually get collected as deletion candidates
 * (the `takeUntil` formula, see [org.edu_sharing.rendering.utils.takeUntil]), and via which
 * [StorageManager] they get deleted. `StorageService.getStorageInfo()` itself (how the scopes are
 * built) is tested separately in `S3StorageServiceTest`.
 */
@ExtendWith(MockKExtension::class)
class CacheCleanerTest {
    private val storageService = mockk<StorageService>()
    private val storageManagerRegistry = mockk<StorageManagerRegistry>()
    private val trackingService = mockk<TrackingService>()

    // Simple, easy-to-verify thresholds: trigger at 80%, clean down to 50%.
    private val lowerThreshold = 0.5f
    private val upperThreshold = 0.8f

    private val underTest = CacheCleaner(storageService, storageManagerRegistry, lowerThreshold, upperThreshold, trackingService)

    private fun entry(repoId: String, nodeId: String, bucket: String, size: Long, lastAccessed: Date = Date()) =
        TrackingEntry.of(repoId = repoId, nodeId = nodeId, hash = "h-$nodeId", type = "image", bucket = bucket, binarySize = size)
            .also { it.lastAccessed = lastAccessed }

    /** Returns all [entries] as a single page — enough for the small lists used in these tests. */
    private fun iteratorOf(entries: List<TrackingEntry>): TrackingService.TrackingIterator =
        TrackingService.TrackingIterator(Function { pageable: Pageable -> PageImpl(entries, pageable, entries.size.toLong()) as Page<TrackingEntry> })

    @Test
    fun `repo scope below the upper threshold triggers no cleanup`() {
        every { storageService.getStorageInfo() } returns listOf(StorageInfo(repoId = "repo1", bucket = null, size = 79, maxSize = 100))

        underTest.cleanCache()

        verify(exactly = 0) { trackingService.getTrackedObjectsByRepoId(any()) }
        verify(exactly = 0) { trackingService.getTrackedObjectsByRepoIdAndBucket(any(), any()) }
    }

    @Test
    fun `scope with maxSize zero is a no-op`() {
        every { storageService.getStorageInfo() } returns listOf(StorageInfo(repoId = "repo1", bucket = null, size = 1000, maxSize = 0))

        underTest.cleanCache()

        verify(exactly = 0) { trackingService.getTrackedObjectsByRepoId(any()) }
    }

    @Test
    fun `repo scope frees down to the lower threshold using the whole-repo LRU iterator`() {
        // size=90/maxSize=100 -> 90% > 80% (upper) triggers cleanup down to 50% = 50 -> bytesToFree = 40
        every { storageService.getStorageInfo() } returns listOf(StorageInfo(repoId = "repo1", bucket = null, size = 90, maxSize = 100))
        val oldest = entry("repo1", "n1", "any-bucket", 25, Date(1000))
        val middle = entry("repo1", "n2", "any-bucket", 20, Date(2000))
        val newest = entry("repo1", "n3", "any-bucket", 50, Date(3000))
        every { trackingService.getTrackedObjectsByRepoId("repo1") } returns iteratorOf(listOf(oldest, middle, newest))

        val manager = mockk<StorageManager>(relaxed = true)
        every { storageManagerRegistry.getBucketManagerByBucketName("any-bucket", "repo1") } returns manager
        every { trackingService.deleteAllTrackedObjects(any()) } returns Unit

        underTest.cleanCache()

        // bytesToFree=40: oldest (25, running total 25 < 40) then middle (20, running total 45 >= 40) -> stop.
        // newest (50) must survive — it's the most recently accessed and would already cover 40 alone,
        // but LRU order means it is never even considered while older entries remain.
        val deleted = slot<List<TrackingEntry>>()
        verify { manager.deleteObjectsFromStorage(capture(deleted)) }
        assertEquals(setOf(oldest, middle), deleted.captured.toSet())
        verify(exactly = 0) { trackingService.getTrackedObjectsByRepoIdAndBucket(any(), any()) }
    }

    @Test
    fun `bucket scope uses the bucket-scoped LRU iterator and only deletes entries of that bucket`() {
        every { storageService.getStorageInfo() } returns listOf(
            StorageInfo(repoId = "repo1", bucket = "rendering2", size = 90, maxSize = 100)
        )
        val candidate = entry("repo1", "n1", "rendering2", 40, Date(1000))
        every { trackingService.getTrackedObjectsByRepoIdAndBucket("repo1", "rendering2") } returns iteratorOf(listOf(candidate))

        val renderingManager = mockk<StorageManager>(relaxed = true)
        every { storageManagerRegistry.getBucketManagerByBucketName("rendering2", "repo1") } returns renderingManager
        every { trackingService.deleteAllTrackedObjects(any()) } returns Unit

        underTest.cleanCache()

        verify(exactly = 0) { trackingService.getTrackedObjectsByRepoId(any()) }
        verify { renderingManager.deleteObjectsFromStorage(listOf(candidate)) }
    }

    @Test
    fun `content bucket scope is routed to a different manager than the rendering bucket`() {
        every { storageService.getStorageInfo() } returns listOf(
            StorageInfo(repoId = "repo1", bucket = "lumi-contentbucket", size = 90, maxSize = 100)
        )
        val candidate = entry("repo1", "n1", "lumi-contentbucket", 40, Date(1000))
        every { trackingService.getTrackedObjectsByRepoIdAndBucket("repo1", "lumi-contentbucket") } returns iteratorOf(listOf(candidate))

        val renderingManager = mockk<StorageManager>(relaxed = true)
        val lumiManager = mockk<StorageManager>(relaxed = true)
        every { storageManagerRegistry.getBucketManagerByBucketName("lumi-contentbucket", "repo1") } returns lumiManager
        every { trackingService.deleteAllTrackedObjects(any()) } returns Unit

        underTest.cleanCache()

        verify { lumiManager.deleteObjectsFromStorage(listOf(candidate)) }
        verify(exactly = 0) { renderingManager.deleteObjectsFromStorage(any()) }
    }

    @Test
    fun `unassigned bucket manager still drops the tracking entries without touching storage`() {
        // StorageManagerRegistry.getBucketManagerByBucketName returning null (no owning manager found)
        // must not fail the run — tracking is cleaned up regardless, matching the pre-existing behavior.
        every { storageService.getStorageInfo() } returns listOf(StorageInfo(repoId = "repo1", bucket = null, size = 90, maxSize = 100))
        val candidate = entry("repo1", "n1", "orphan-bucket", 50, Date(1000))
        every { trackingService.getTrackedObjectsByRepoId("repo1") } returns iteratorOf(listOf(candidate))
        every { storageManagerRegistry.getBucketManagerByBucketName("orphan-bucket", "repo1") } returns null
        every { trackingService.deleteAllTrackedObjects(any()) } returns Unit

        underTest.cleanCache()

        verify { trackingService.deleteAllTrackedObjects(listOf(candidate)) }
    }

    @Test
    fun `takeUntil frees at least bytesToFree bytes, not merely down to the lower-threshold ratio`() {
        // Regression test for the fixed formula (see utils/SequenceExtensions.kt takeUntil):
        // size=1000/maxSize=1000 (100%) -> target 50% = 500 -> bytesToFree = 500, not 500 total remaining.
        every { storageService.getStorageInfo() } returns listOf(StorageInfo(repoId = "repo1", bucket = null, size = 1000, maxSize = 1000))
        val entries = (1..10).map { entry("repo1", "n$it", "b", 100, Date(it.toLong())) }
        every { trackingService.getTrackedObjectsByRepoId("repo1") } returns iteratorOf(entries)
        val manager = mockk<StorageManager>(relaxed = true)
        every { storageManagerRegistry.getBucketManagerByBucketName("b", "repo1") } returns manager
        every { trackingService.deleteAllTrackedObjects(any()) } returns Unit

        underTest.cleanCache()

        val deleted = slot<List<TrackingEntry>>()
        verify { manager.deleteObjectsFromStorage(capture(deleted)) }
        val freed = deleted.captured.sumOf { it.binarySize }
        assertTrue(freed >= 500, "expected to free at least 500 bytes, freed $freed")
        assertEquals(5, deleted.captured.size)
    }
}
