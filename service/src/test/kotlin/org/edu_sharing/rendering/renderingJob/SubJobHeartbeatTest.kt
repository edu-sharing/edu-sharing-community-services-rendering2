package org.edu_sharing.rendering.renderingJob

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SubJobHeartbeatTest {

    private val subJobRepository: SubJobRepository = mockk(relaxed = true)
    private val heartbeat = SubJobHeartbeat(subJobRepository)

    @Test
    fun `returns the block's result`() {
        val result = heartbeat.run(ObjectId(), Duration.ofMinutes(5)) { "done" }

        assertEquals("done", result)
    }

    @Test
    fun `propagates an exception thrown by the block`() {
        assertFailsWith<IllegalStateException> {
            heartbeat.run(ObjectId(), Duration.ofMinutes(5)) { throw IllegalStateException("boom") }
        }
    }

    @Test
    fun `touches the sub-job periodically while the block is running`() {
        val subJobId = ObjectId()

        heartbeat.run(subJobId, Duration.ofMillis(20)) {
            Thread.sleep(100)
        }

        verify(atLeast = 1) { subJobRepository.touchLastModifiedDate(subJobId) }
    }

    @Test
    fun `a failing touch does not interrupt the running block`() {
        val subJobId = ObjectId()
        every { subJobRepository.touchLastModifiedDate(subJobId) } throws RuntimeException("mongo hiccup")

        val result = heartbeat.run(subJobId, Duration.ofMillis(20)) {
            Thread.sleep(60)
            "still finished"
        }

        assertEquals("still finished", result)
    }
}
