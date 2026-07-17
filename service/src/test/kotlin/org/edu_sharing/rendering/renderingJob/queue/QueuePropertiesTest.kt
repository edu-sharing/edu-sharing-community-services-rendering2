package org.edu_sharing.rendering.renderingJob.queue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueuePropertiesTest {

    private fun spec(mode: QueueMode, concurrency: Int = 4, prefetch: Int? = null) =
        QueueSpec().apply {
            this.mode = mode
            this.concurrency = concurrency
            this.prefetch = prefetch
        }

    @Test
    fun `STANDARD uses concurrency as the registered consumer count`() {
        assertEquals(4, spec(QueueMode.STANDARD, concurrency = 4).effectiveConcurrency)
    }

    @Test
    fun `IMPORT registers a single channel regardless of concurrency`() {
        assertEquals(1, spec(QueueMode.IMPORT, concurrency = 50).effectiveConcurrency)
    }

    @Test
    fun `SINGLE_ACTIVE is forced to one consumer`() {
        assertEquals(1, spec(QueueMode.SINGLE_ACTIVE, concurrency = 8).effectiveConcurrency)
    }

    @Test
    fun `singleActiveConsumer is true only for SINGLE_ACTIVE`() {
        assertTrue(spec(QueueMode.SINGLE_ACTIVE).singleActiveConsumer)
        assertFalse(spec(QueueMode.STANDARD).singleActiveConsumer)
        assertFalse(spec(QueueMode.IMPORT).singleActiveConsumer)
    }

    @Test
    fun `import prefetch defaults to concurrency and honours an explicit override`() {
        assertEquals(50, spec(QueueMode.IMPORT, concurrency = 50).effectiveImportPrefetch)
        assertEquals(80, spec(QueueMode.IMPORT, concurrency = 50, prefetch = 80).effectiveImportPrefetch)
    }

    @Test
    fun `validate rejects SINGLE_ACTIVE on a queue that does not declare the argument`() {
        val props = QueueProperties().apply { image.mode = QueueMode.SINGLE_ACTIVE }
        assertThrows(IllegalArgumentException::class.java) { props.validate() }
    }

    @Test
    fun `validate allows SINGLE_ACTIVE on moodle and h5p`() {
        val props = QueueProperties().apply {
            moodle.mode = QueueMode.SINGLE_ACTIVE
            h5p.mode = QueueMode.SINGLE_ACTIVE
        }
        props.validate()
    }

    @Test
    fun `validate rejects an import prefetch below concurrency`() {
        val props = QueueProperties().apply {
            sodix.mode = QueueMode.IMPORT
            sodix.concurrency = 50
            sodix.prefetch = 10
        }
        assertThrows(IllegalArgumentException::class.java) { props.validate() }
    }
}
