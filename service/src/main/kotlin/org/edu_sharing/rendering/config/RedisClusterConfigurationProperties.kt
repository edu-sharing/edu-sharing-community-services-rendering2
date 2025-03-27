package org.edu_sharing.rendering.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component


@Component
@ConfigurationProperties(prefix = "spring.redis.cluster")
@ConditionalOnProperty(name = ["spring.redis.cluster.nodes"])
class RedisClusterConfigurationProperties {
    /**
     * Get initial collection of known cluster nodes in format `host:port`.
     *
     * @return
     */
    /*
         * spring.redis.cluster.nodes[0] = 127.0.0.1:7379
         * spring.redis.cluster.nodes[1] = 127.0.0.1:7380
         * ...
         */
    lateinit var nodes: List<String>
    var maxRedirects: Int? = null
}
