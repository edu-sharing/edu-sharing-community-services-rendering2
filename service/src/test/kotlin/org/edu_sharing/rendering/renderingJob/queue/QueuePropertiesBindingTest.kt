package org.edu_sharing.rendering.renderingJob.queue

import org.edu_sharing.rendering.modules.sodix.SodixQueueProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.mock.env.MockEnvironment

class QueuePropertiesBindingTest {

    @Test
    fun `binds name, prefetch and nested scaling from the queue prefix, ignoring key and concurrency`() {
        val env = MockEnvironment()
            .withProperty("app.queue.sodix.name", "sodix_job_queue")
            .withProperty("app.queue.sodix.key", "sodix_job")      // consumed only by the @RabbitListener placeholder
            .withProperty("app.queue.sodix.concurrency", "1-200")  // consumed only by the @RabbitListener placeholder
            .withProperty("app.queue.sodix.prefetch", "3")
            .withProperty("app.queue.sodix.scaling.startConsumerMinInterval", "1000")
            .withProperty("app.queue.sodix.scaling.consecutiveActiveTrigger", "1")

        val props = Binder(ConfigurationPropertySources.get(env))
            .bindOrCreate("app.queue.sodix", SodixQueueProperties::class.java)

        assertEquals("sodix_job_queue", props.name)
        assertEquals(3, props.prefetch)
        // nested scaling: set knobs bind, unset ones stay null (framework default)
        assertEquals(1000L, props.scaling.startConsumerMinInterval)
        assertEquals(1, props.scaling.consecutiveActiveTrigger)
        assertNull(props.scaling.stopConsumerMinInterval)
        assertNull(props.scaling.consecutiveIdleTrigger)
    }
}
