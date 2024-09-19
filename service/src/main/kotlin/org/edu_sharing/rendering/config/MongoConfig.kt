package org.edu_sharing.rendering.config

import com.mongodb.WriteConcern
import org.edu_sharing.rendering.edusharingRepo.AppConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.mongodb.config.EnableMongoAuditing
import org.springframework.data.mongodb.core.MongoAction
import org.springframework.data.mongodb.core.WriteConcernResolver
import java.time.OffsetDateTime
import java.util.*

@EnableMongoAuditing
@Configuration
class MongoConfig {

    @Bean
    fun writeConcernResolver(): WriteConcernResolver {
        return WriteConcernResolver { action: MongoAction? ->
            when {
                action == null -> WriteConcern.UNACKNOWLEDGED
                action.entityType == AppConfig::class.java -> WriteConcern.ACKNOWLEDGED
                else -> WriteConcern.UNACKNOWLEDGED
            }
        }
    }

    @Bean
    fun auditingDateTimeProvider(): DateTimeProvider {
        return DateTimeProvider { Optional.of(OffsetDateTime.now()) }
    }
}