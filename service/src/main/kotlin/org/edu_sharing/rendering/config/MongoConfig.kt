package org.edu_sharing.rendering.config

import com.mongodb.WriteConcern
import org.edu_sharing.rendering.edusharingRepo.entity.ExternalBucket
import org.edu_sharing.rendering.edusharingRepo.entity.RendererKeyConfig
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
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

@EnableMongoAuditing
@Configuration
class MongoConfig {

    @Bean
    fun writeConcernResolver(): WriteConcernResolver {
        return WriteConcernResolver { action: MongoAction? ->
            when {
                action == null -> WriteConcern.UNACKNOWLEDGED
                action.entityType == RendererKeyConfig::class.java -> WriteConcern.ACKNOWLEDGED
                action.entityType == RenderingJob::class.java -> WriteConcern.ACKNOWLEDGED
                action.entityType == SubJob::class.java -> WriteConcern.ACKNOWLEDGED
                action.collectionName == "renderingJob" -> WriteConcern.ACKNOWLEDGED
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
