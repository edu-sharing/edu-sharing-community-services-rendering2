package org.edu_sharing.rendering.cacheCleaner

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.edu_sharing.rendering.storage.StorageInfo
import org.edu_sharing.rendering.storage.StorageScopeKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageQuotaMetricsTest {
    private val registry = SimpleMeterRegistry()
    private val underTest = StorageQuotaMetrics(registry)

    private fun gauge(name: String, repo: String = "repo1", bucket: String = "rendering2", kind: String = "bucket"): Double? =
        registry.find(name).tags("repo", repo, "bucket", bucket, "kind", kind).gauge()?.value()

    @Test
    fun `a scope reports its size, quota and ratio`() {
        underTest.record(StorageInfo("repo1", "rendering2", size = 40, maxSize = 100), 40, 0.8f)

        assertEquals(40.0, gauge("rendering.storage.used.bytes"))
        assertEquals(100.0, gauge("rendering.storage.quota.bytes"))
        assertEquals(0.4, gauge("rendering.storage.usage.ratio"))
        assertEquals(0.0, gauge("rendering.storage.over.quota"))
        assertEquals(0.0, gauge("rendering.storage.over.threshold"))
    }

    @Test
    fun `above the upper threshold is flagged, above the quota is flagged separately`() {
        val scope = StorageInfo("repo1", "rendering2", size = 90, maxSize = 100)

        underTest.record(scope, 90, 0.8f)
        assertEquals(1.0, gauge("rendering.storage.over.threshold"))
        assertEquals(0.0, gauge("rendering.storage.over.quota"), "90 of 100 is still within the quota")

        underTest.record(scope, 101, 0.8f)
        assertEquals(1.0, gauge("rendering.storage.over.quota"))
    }

    @Test
    fun `a later record replaces the earlier one - the estimate after a cleanup`() {
        val scope = StorageInfo("repo1", "rendering2", size = 120, maxSize = 100)
        underTest.record(scope, 120, 0.8f)
        assertEquals(1.0, gauge("rendering.storage.over.quota"))

        underTest.record(scope, 50, 0.8f)

        assertEquals(0.0, gauge("rendering.storage.over.quota"))
        assertEquals(50.0, gauge("rendering.storage.used.bytes"))
    }

    @Test
    fun `a scope without a quota is never over it`() {
        underTest.record(StorageInfo("repo1", "rendering2", size = 5_000, maxSize = 0), 5_000, 0.8f)

        assertEquals(5_000.0, gauge("rendering.storage.used.bytes"))
        assertEquals(0.0, gauge("rendering.storage.usage.ratio"))
        assertEquals(0.0, gauge("rendering.storage.over.quota"))
        assertEquals(0.0, gauge("rendering.storage.over.threshold"))
    }

    @Test
    fun `repo-wide and library cache scopes are told apart by their tags`() {
        underTest.record(StorageInfo("repo1", null, size = 10, maxSize = 100), 10, 0.8f)
        underTest.record(
            StorageInfo("repo1", "lumi-contentbucket", size = 70, maxSize = 100, kind = StorageScopeKind.LIBRARY_CACHE), 70, 0.8f
        )

        assertEquals(10.0, gauge("rendering.storage.used.bytes", bucket = "*"))
        assertEquals(70.0, gauge("rendering.storage.used.bytes", bucket = "lumi-contentbucket", kind = "library_cache"))
        assertNull(gauge("rendering.storage.used.bytes", bucket = "lumi-contentbucket", kind = "bucket"))
    }

    @Test
    fun `scopes that are gone stop reporting`() {
        val kept = StorageInfo("repo1", "rendering2", size = 1, maxSize = 100)
        val gone = StorageInfo("repo2", "rendering2", size = 1, maxSize = 100)
        underTest.record(kept, 1, 0.8f)
        underTest.record(gone, 1, 0.8f)

        underTest.retainOnly(listOf(kept))

        assertEquals(1.0, gauge("rendering.storage.used.bytes", repo = "repo1"))
        assertNull(gauge("rendering.storage.used.bytes", repo = "repo2"))
    }

    @Test
    fun `cleaner outcomes are counted per kind and the last run is timestamped`() {
        underTest.cleaned(StorageScopeKind.BUCKET, entries = 3, bytes = 300)
        underTest.cleaned(StorageScopeKind.BUCKET, entries = 1, bytes = 50)
        underTest.cleaned(StorageScopeKind.LIBRARY_CACHE, entries = 1, bytes = 20)
        underTest.failure(StorageScopeKind.BUCKET)

        assertEquals(4.0, counter("rendering.cache.cleaner.deleted.entries", "bucket"))
        assertEquals(350.0, counter("rendering.cache.cleaner.freed.bytes", "bucket"))
        assertEquals(20.0, counter("rendering.cache.cleaner.freed.bytes", "library_cache"))
        assertEquals(1.0, counter("rendering.cache.cleaner.failures", "bucket"))

        assertEquals(0.0, registry.get("rendering.cache.cleaner.last.run.timestamp.seconds").gauge().value())
        underTest.runFinished()
        assertTrue(registry.get("rendering.cache.cleaner.last.run.timestamp.seconds").gauge().value() > 1_700_000_000)
    }

    private fun counter(name: String, kind: String) = (registry as MeterRegistry).get(name).tag("kind", kind).counter().count()
}
