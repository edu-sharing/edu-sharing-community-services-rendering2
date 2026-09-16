package org.edu_sharing.rendering.renderingJob.queue

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.QueueInformation

class LegacyQueueCleanerTest {

    private val amqpAdmin: AmqpAdmin = mockk(relaxed = true)
    private val properties = LegacyQueueCleanupProperties().apply {
        legacyQueueNames = mutableListOf("already_gone_queue", "busy_queue", "empty_queue", "queue_with_messages")
    }
    private val cleaner = LegacyQueueCleaner(amqpAdmin, properties)

    @Test
    fun `does nothing for a queue that no longer exists`() {
        every { amqpAdmin.getQueueInfo("already_gone_queue") } returns null
        every { amqpAdmin.getQueueInfo(neq("already_gone_queue")) } returns QueueInformation("x", 0, 1)

        cleaner.cleanup()

        verify(exactly = 0) { amqpAdmin.deleteQueue("already_gone_queue", any(), any()) }
    }

    @Test
    fun `does not delete a queue that still has an active consumer`() {
        every { amqpAdmin.getQueueInfo("busy_queue") } returns QueueInformation("busy_queue", 5, 2)
        every { amqpAdmin.getQueueInfo(neq("busy_queue")) } returns null

        cleaner.cleanup()

        verify(exactly = 0) { amqpAdmin.deleteQueue("busy_queue", any(), any()) }
    }

    @Test
    fun `deletes an orphaned empty queue`() {
        every { amqpAdmin.getQueueInfo("empty_queue") } returns QueueInformation("empty_queue", 0, 0)
        every { amqpAdmin.getQueueInfo(neq("empty_queue")) } returns null

        cleaner.cleanup()

        verify(exactly = 1) { amqpAdmin.deleteQueue("empty_queue", true, false) }
    }

    @Test
    fun `deletes an orphaned queue even if it still has unconsumed messages`() {
        every { amqpAdmin.getQueueInfo("queue_with_messages") } returns QueueInformation("queue_with_messages", 42, 0)
        every { amqpAdmin.getQueueInfo(neq("queue_with_messages")) } returns null

        cleaner.cleanup()

        verify(exactly = 1) { amqpAdmin.deleteQueue("queue_with_messages", true, false) }
    }

    @Test
    fun `an exception deleting one queue does not stop the others from being processed`() {
        every { amqpAdmin.getQueueInfo("empty_queue") } returns QueueInformation("empty_queue", 0, 0)
        every { amqpAdmin.getQueueInfo("queue_with_messages") } returns QueueInformation("queue_with_messages", 1, 0)
        every { amqpAdmin.getQueueInfo("already_gone_queue") } returns null
        every { amqpAdmin.getQueueInfo("busy_queue") } returns null
        every { amqpAdmin.deleteQueue("empty_queue", true, false) } throws RuntimeException("lost the unused race")

        cleaner.cleanup()

        verify(exactly = 1) { amqpAdmin.deleteQueue("empty_queue", true, false) }
        verify(exactly = 1) { amqpAdmin.deleteQueue("queue_with_messages", true, false) }
    }
}
