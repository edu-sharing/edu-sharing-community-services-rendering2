package org.edu_sharing.rendering.edusharingRepo.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize

/**
 * Verifies that quota values bound from properties (`RepositoryRegistrationConfigItem.quota` and
 * `ExternalBucketConfig.quota`, both `DataSize` with `@DataSizeUnit(DataUnit.BYTES)`) accept both a
 * plain byte count and a human-readable size like `1GB`/`500MB` — Spring's binary units (1KB = 1024
 * bytes), not decimal — and that [ExternalBucketsConfig.toExternalBuckets] correctly converts them
 * to the byte-`Long`-based [ExternalBuckets] used everywhere else (Mongo, the admin API).
 */
class RepositoryRegistrationConfigTest {

    @Configuration
    @EnableConfigurationProperties(RepositoryRegistrationConfig::class)
    class TestConfig

    private val contextRunner = ApplicationContextRunner().withUserConfiguration(TestConfig::class.java)

    @Test
    fun `quota accepts a human-readable data size`() {
        contextRunner
            .withPropertyValues(
                "app.repository.registration.id.test.url=https://repo.example.org",
                "app.repository.registration.id.test.username=admin",
                "app.repository.registration.id.test.password=secret",
                "app.repository.registration.id.test.quota=1GB",
                "app.repository.registration.id.test.externalBuckets.renderingBucket.name=rendering2",
                "app.repository.registration.id.test.externalBuckets.renderingBucket.quota=500MB",
            )
            .run { context ->
                val item = context.getBean(RepositoryRegistrationConfig::class.java).id["test"]!!
                assertEquals(DataSize.ofGigabytes(1), item.quota)
                assertEquals(DataSize.ofMegabytes(500), item.externalBuckets?.renderingBucket?.quota)

                // What actually reaches Mongo / the admin API — plain bytes, unaffected by this feature.
                val registration = item.externalBuckets!!.toExternalBuckets()
                assertEquals(1024L * 1024 * 1024, item.quota.toBytes())
                assertEquals(500L * 1024 * 1024, registration.renderingBucket?.quota)
            }
    }

    @Test
    fun `quota still accepts a plain byte count without a suffix`() {
        contextRunner
            .withPropertyValues(
                "app.repository.registration.id.test.url=https://repo.example.org",
                "app.repository.registration.id.test.username=admin",
                "app.repository.registration.id.test.password=secret",
                "app.repository.registration.id.test.quota=1073741824",
                "app.repository.registration.id.test.externalBuckets.renderingBucket.name=rendering2",
                "app.repository.registration.id.test.externalBuckets.renderingBucket.quota=0",
            )
            .run { context ->
                val item = context.getBean(RepositoryRegistrationConfig::class.java).id["test"]!!
                assertEquals(1073741824L, item.quota.toBytes())
                assertEquals(0L, item.externalBuckets?.renderingBucket?.quota?.toBytes())
            }
    }
}
