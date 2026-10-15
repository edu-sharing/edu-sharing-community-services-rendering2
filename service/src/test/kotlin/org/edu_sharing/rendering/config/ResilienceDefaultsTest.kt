package org.edu_sharing.rendering.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.support.PropertiesLoaderUtils

/**
 * Guards the production defaults of `src/main/resources/application.properties` (the test classpath has its own
 * `application.properties` that shadows it, so the main file is read from disk).
 */
class ResilienceDefaultsTest {

    private val props = PropertiesLoaderUtils.loadProperties(FileSystemResource("src/main/resources/application.properties"))

    @Test
    fun `liveness group stays independent of subsystems`() {
        assertEquals("livenessState", props.getProperty("management.endpoint.health.group.liveness.include"))
    }

    @Test
    fun `readiness group carries mongo and redis`() {
        val include = props.getProperty("management.endpoint.health.group.readiness.include").split(',')
        assertEquals(listOf("readinessState", "mongo", "redis"), include)
    }

    @Test
    fun `graceful shutdown waits longer than the rabbit container shutdown timeout`() {
        assertEquals("graceful", props.getProperty("server.shutdown"))
        assertEquals("36m", props.getProperty("spring.lifecycle.timeout-per-shutdown-phase"))
    }

    @Test
    fun `rabbit has bounded timeouts and a heartbeat`() {
        listOf(
            "spring.rabbitmq.connection-timeout",
            "spring.rabbitmq.channel-rpc-timeout",
            "spring.rabbitmq.requested-heartbeat",
            "spring.rabbitmq.cache.channel.checkout-timeout"
        ).forEach { assertFalse(props.getProperty(it).isNullOrBlank(), it) }
    }
}
