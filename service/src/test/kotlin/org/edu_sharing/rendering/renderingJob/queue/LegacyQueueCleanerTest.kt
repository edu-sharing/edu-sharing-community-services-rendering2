package org.edu_sharing.rendering.renderingJob.queue

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ShutdownSignalException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.amqp.AmqpIOException
import org.springframework.amqp.rabbit.core.ChannelCallback
import org.springframework.amqp.rabbit.core.RabbitOperations
import java.io.IOException

class LegacyQueueCleanerTest {

    private val channel: Channel = mockk()
    private val rabbitOperations: RabbitOperations = mockk {
        every { execute(any<ChannelCallback<Any?>>()) } answers { firstArg<ChannelCallback<Any?>>().doInRabbit(channel) }
    }
    private val properties = LegacyQueueCleanupProperties().apply {
        legacyQueueNames = mutableListOf("busy_queue", "orphaned_queue")
    }
    private val cleaner = LegacyQueueCleaner(rabbitOperations, properties)

    private fun deleteOk(messageCount: Int) = AMQP.Queue.DeleteOk.Builder().messageCount(messageCount).build()

    private fun inUse() = AmqpIOException(
        IOException(
            ShutdownSignalException(
                false, false,
                AMQP.Channel.Close.Builder().replyCode(AMQP.PRECONDITION_FAILED).replyText("PRECONDITION_FAILED - queue in use").build(),
                null,
            )
        )
    )

    @Test
    fun `deletes every legacy queue with if-unused and without if-empty, never probing it first`() {
        every { channel.queueDelete(any(), any(), any()) } returns deleteOk(0)

        cleaner.cleanup()

        verify(exactly = 1) { channel.queueDelete("busy_queue", true, false) }
        verify(exactly = 1) { channel.queueDelete("orphaned_queue", true, false) }
        verify(exactly = 0) { channel.queueDeclarePassive(any()) }
    }

    @Test
    fun `a queue that still has a consumer does not stop the others from being processed`() {
        every { channel.queueDelete("busy_queue", true, false) } throws inUse()
        every { channel.queueDelete("orphaned_queue", true, false) } returns deleteOk(42)

        cleaner.cleanup()

        verify(exactly = 1) { channel.queueDelete("orphaned_queue", true, false) }
    }

    @Test
    fun `an unexpected exception for one queue does not stop the others from being processed`() {
        every { channel.queueDelete("busy_queue", true, false) } throws RuntimeException("connection lost")
        every { channel.queueDelete("orphaned_queue", true, false) } returns deleteOk(0)

        cleaner.cleanup()

        verify(exactly = 1) { channel.queueDelete("orphaned_queue", true, false) }
    }
}
