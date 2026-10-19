package org.edu_sharing.rendering.config

import com.mongodb.MongoClientSettings
import com.mongodb.WriteConcern
import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.edusharingRepo.entity.RendererKeyConfig
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoAction
import org.springframework.data.mongodb.core.MongoActionOperation
import java.time.Duration
import java.util.concurrent.TimeUnit

class MongoConfigTest {

    private val underTest = MongoConfig()

    @Test
    fun `timeout customizer applies connect, socket and server selection timeouts`() {
        val timeouts = MongoTimeoutProperties(Duration.ofSeconds(7), Duration.ofMinutes(2), Duration.ofSeconds(15))
        val builder = MongoClientSettings.builder()

        underTest.mongoTimeoutCustomizer(timeouts).customize(builder)
        val settings = builder.build()

        assertEquals(7_000, settings.socketSettings.getConnectTimeout(TimeUnit.MILLISECONDS))
        assertEquals(120_000, settings.socketSettings.getReadTimeout(TimeUnit.MILLISECONDS))
        assertEquals(15_000, settings.clusterSettings.getServerSelectionTimeout(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `all persisted entities are written acknowledged`() {
        val resolver = underTest.writeConcernResolver()
        listOf(
            RendererKeyConfig::class.java,
            RenderingJob::class.java,
            SubJob::class.java,
            TrackingEntry::class.java,
            RepositoryRegistration::class.java
        ).forEach { type ->
            val action = MongoAction(null, MongoActionOperation.SAVE, "any", type, null, null)
            assertEquals(WriteConcern.ACKNOWLEDGED, resolver.resolve(action), type.simpleName)
        }
    }

    @Test
    fun `tracking collection is acknowledged even without an entity type`() {
        val action = MongoAction(null, MongoActionOperation.UPDATE, "Tracking", null, null, null)
        assertEquals(WriteConcern.ACKNOWLEDGED, underTest.writeConcernResolver().resolve(action))
    }
}
