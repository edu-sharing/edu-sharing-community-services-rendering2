package org.edu_sharing.rendering.renderingJob.queue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the per-mode semantics encapsulated by [QueueSpec] and its three mode-fixing base classes. The
 * mode is fixed by which base class a queue extends (not by configuration), so there is no longer a runtime
 * whitelist to test — a queue simply cannot be the wrong mode. Each base is exercised through a minimal
 * concrete stand-in that mirrors what a module's `@ConfigurationProperties` bean looks like.
 */
class QueueSpecTest {

    private class TestStandard : StandardQueueProperties()
    private class TestImport : ImportQueueProperties()
    private class TestSingleActive : SingleActiveQueueProperties()

    @Test
    fun `STANDARD uses concurrency as the registered consumer count`() {
        assertEquals(4, TestStandard().apply { concurrency = 4 }.effectiveConcurrency)
    }

    @Test
    fun `IMPORT registers a single channel regardless of concurrency`() {
        assertEquals(1, TestImport().apply { concurrency = 50 }.effectiveConcurrency)
    }

    @Test
    fun `SINGLE_ACTIVE is forced to one consumer`() {
        assertEquals(1, TestSingleActive().apply { concurrency = 8 }.effectiveConcurrency)
    }

    @Test
    fun `singleActiveConsumer is true only for SINGLE_ACTIVE`() {
        assertTrue(TestSingleActive().singleActiveConsumer)
        assertFalse(TestStandard().singleActiveConsumer)
        assertFalse(TestImport().singleActiveConsumer)
    }

    @Test
    fun `import prefetch defaults to concurrency and honours an explicit override`() {
        assertEquals(50, TestImport().apply { concurrency = 50 }.effectiveImportPrefetch)
        assertEquals(80, TestImport().apply { concurrency = 50; prefetch = 80 }.effectiveImportPrefetch)
    }

    @Test
    fun `validate rejects an import prefetch below concurrency`() {
        val spec = TestImport().apply { concurrency = 50; prefetch = 10 }
        assertThrows(IllegalArgumentException::class.java) { spec.validateImportPrefetch() }
    }
}
