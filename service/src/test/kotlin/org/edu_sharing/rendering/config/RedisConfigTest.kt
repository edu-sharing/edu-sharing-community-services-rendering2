package org.edu_sharing.rendering.config

import io.lettuce.core.ClientOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.time.Duration

class RedisConfigTest {

    private val client = RedisClientProperties(
        commandTimeout = Duration.ofSeconds(3),
        connectTimeout = Duration.ofSeconds(4),
        keepAlive = true,
        requestQueueSize = 123,
        disconnectedBehavior = ClientOptions.DisconnectedBehavior.REJECT_COMMANDS
    )

    @Test
    fun `standalone client gets the same bounded options as the cluster client`() {
        val configuration = RedisConfig().standaloneClientConfiguration(client)

        val options = configuration.clientOptions.orElseThrow()
        assertEquals(123, options.requestQueueSize)
        assertEquals(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS, options.disconnectedBehavior)
        assertTrue(options.socketOptions.isKeepAlive)
        assertEquals(Duration.ofSeconds(4), options.socketOptions.connectTimeout)
        assertTrue(options.timeoutOptions.isTimeoutCommands)
        assertEquals(Duration.ofSeconds(3), configuration.commandTimeout)
    }

    @Test
    fun `defaults bind to bounded values`() {
        val bound = Binder(MapConfigurationPropertySource(emptyMap<String, Any>()))
            .bindOrCreate("app.redis.client", RedisClientProperties::class.java)

        assertEquals(Duration.ofSeconds(5), bound.commandTimeout)
        assertEquals(2000, bound.requestQueueSize)
        assertEquals(ClientOptions.DisconnectedBehavior.DEFAULT, bound.disconnectedBehavior)
    }
}
