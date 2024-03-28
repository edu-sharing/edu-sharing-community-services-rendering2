package org.edu_sharing.rendering.config

import com.mongodb.WriteConcern
import org.edu_sharing.rendering.entity.AppConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoAction
import org.springframework.data.mongodb.core.WriteConcernResolver


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
}