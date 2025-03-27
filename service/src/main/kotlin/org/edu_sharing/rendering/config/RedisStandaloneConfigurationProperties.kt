package org.edu_sharing.rendering.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import kotlin.properties.Delegates


@Component
@ConfigurationProperties(prefix = "spring.redis.standalone")
class RedisStandaloneConfigurationProperties {

    lateinit var host: String
    var port by Delegates.notNull<Int>()
}
