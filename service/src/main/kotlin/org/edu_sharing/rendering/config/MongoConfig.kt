package org.edu_sharing.rendering.config

import com.mongodb.WriteConcern
import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.edusharingRepo.entity.ExternalBucket
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.entity.RendererKeyConfig
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.config.EnableMongoAuditing
import org.springframework.data.mongodb.core.MongoAction
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.WriteConcernResolver
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

@EnableMongoAuditing
@EnableConfigurationProperties(MongoTimeoutProperties::class)
@Configuration
class MongoConfig {

    /**
     * Sets the driver timeouts explicitly (see [MongoTimeoutProperties]); without them a hung
     * connection blocks its caller indefinitely (`socketTimeoutMS` defaults to infinite).
     */
    @Bean
    fun mongoTimeoutCustomizer(timeouts: MongoTimeoutProperties): MongoClientSettingsBuilderCustomizer =
        MongoClientSettingsBuilderCustomizer { builder ->
            builder.applyToSocketSettings { socket ->
                socket.connectTimeout(timeouts.connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                socket.readTimeout(timeouts.socketTimeout.toMillis(), TimeUnit.MILLISECONDS)
            }
            builder.applyToClusterSettings { cluster ->
                cluster.serverSelectionTimeout(timeouts.serverSelectionTimeout.toMillis(), TimeUnit.MILLISECONDS)
            }
        }

    /**
     * Write concern per entity. Everything that is persisted through this template is
     * ACKNOWLEDGED: there is no documented performance decision behind the former UNACKNOWLEDGED
     * default for tracking/registration data, and a silently lost `TrackingEntry` skews the cache
     * cleaner's size accounting (MEMORY_AUDIT F17), a lost registration write would not surface
     * at all. UNACKNOWLEDGED remains only as the fallback for an unknown/absent entity, which no
     * current write path hits.
     */
    @Bean
    fun writeConcernResolver(): WriteConcernResolver {
        return WriteConcernResolver { action: MongoAction? ->
            when {
                action == null -> WriteConcern.UNACKNOWLEDGED
                action.entityType == RendererKeyConfig::class.java -> WriteConcern.ACKNOWLEDGED
                action.entityType == RenderingJob::class.java -> WriteConcern.ACKNOWLEDGED
                action.entityType == SubJob::class.java -> WriteConcern.ACKNOWLEDGED
                action.entityType == TrackingEntry::class.java -> WriteConcern.ACKNOWLEDGED
                action.entityType == RepositoryRegistration::class.java -> WriteConcern.ACKNOWLEDGED
                action.collectionName == "renderingJob" -> WriteConcern.ACKNOWLEDGED
                action.collectionName == "Tracking" -> WriteConcern.ACKNOWLEDGED
                else -> WriteConcern.UNACKNOWLEDGED
            }
        }
    }

    @Bean
    fun mongoTemplate(mongoDatabaseFactory: MongoDatabaseFactory, converter: MappingMongoConverter): MongoTemplate {
        val mongoTemplate = MongoTemplate(mongoDatabaseFactory, converter)
        mongoTemplate.setWriteConcernResolver(writeConcernResolver())
        return mongoTemplate
    }

    @Bean
    fun auditingDateTimeProvider(): DateTimeProvider {
        return DateTimeProvider { Optional.of(Instant.now()) }
    }

    /**
     * Reads older `RepositoryRegistration` documents whose `buckets.renderingBucket`/`tempBucket`
     * still holds a plain string (from before the switch to [ExternalBucket] with a quota) as a
     * bucket without a quota. Register only as a [ReadingConverter] — a `WritingConverter` would
     * turn [ExternalBucket] into a Mongo simple type and make it get written as a scalar again
     * instead of a sub-document. Becomes redundant in the long run thanks to the one-time migration
     * runner (`ExternalBucketMigrationRunner`), but stays in place as a safety net for rolling deploys.
     */
    @ReadingConverter
    class StringToExternalBucketConverter : Converter<String, ExternalBucket> {
        override fun convert(source: String): ExternalBucket = ExternalBucket(name = source, quota = 0)
    }

    @Bean
    fun mongoCustomConversions(): MongoCustomConversions {
        return MongoCustomConversions(listOf(StringToExternalBucketConverter()))
    }
}
