package org.edu_sharing.rendering.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.time.Duration

class S3ConfigTest {

    private val client = S3ClientProperties(
        maxConnections = 77,
        connectionTimeout = Duration.ofSeconds(3),
        socketTimeout = Duration.ofSeconds(4),
        connectionAcquisitionTimeout = Duration.ofSeconds(5),
        connectionMaxIdleTime = Duration.ofSeconds(6),
        apiCallAttemptTimeout = Duration.ofMinutes(10),
        apiCallTimeout = Duration.ofMinutes(20)
    )

    private fun config(trustAll: Boolean) = S3Config().apply {
        accessKeyId = "key"
        secretAccessKey = "secret"
        region = "eu-central-1"
        url = "https://s3.example.org"
        trustAllCertificates = trustAll
    }

    @Test
    fun `client builds with explicit http client`() {
        config(trustAll = false).s3Client(client).close()
    }

    @Test
    fun `client builds with explicit http client in trust-all mode`() {
        val config = config(trustAll = true)
        config.apacheHttpClientBuilder(client).build().close()
        config.s3Client(client).close()
    }

    @Test
    fun `call timeouts are generous and configurable`() {
        val override = config(false).overrideConfiguration(client)
        assertEquals(Duration.ofMinutes(10), override.apiCallAttemptTimeout().orElseThrow())
        assertEquals(Duration.ofMinutes(20), override.apiCallTimeout().orElseThrow())
    }

    @Test
    fun `defaults fit the consumer parallelism of a pod`() {
        val bound = Binder(MapConfigurationPropertySource(emptyMap<String, Any>()))
            .bindOrCreate("app.s3.client", S3ClientProperties::class.java)
        assertEquals(200, bound.maxConnections)
        assertEquals(Duration.ofMinutes(30), bound.apiCallAttemptTimeout)
        assertEquals(Duration.ofHours(1), bound.apiCallTimeout)
    }
}
