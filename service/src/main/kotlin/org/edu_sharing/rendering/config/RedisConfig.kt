package org.edu_sharing.rendering.config

import io.lettuce.core.ReadFrom
import io.lettuce.core.SocketOptions
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisClusterConfiguration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
class RedisConfig {


    @Bean
    @ConditionalOnBean(RedisClusterConfigurationProperties::class)
    fun lettuceClusterConnectionFactory(configurationProperties: RedisClusterConfigurationProperties): RedisConnectionFactory {
        val redisConfig = RedisClusterConfiguration(configurationProperties.nodes)

        configurationProperties.maxRedirects?.let { redisConfig.setMaxRedirects(it) }

        // In k8s the cluster nodes are seeded via a single Service DNS name; Lettuce discovers
        // the actual pod IPs once and – without refresh – keeps talking to them directly. When a
        // Redis pod is rescheduled its IP changes, so the cached topology goes stale and
        // connections intermittently fail even though the cluster itself is healthy. Enable
        // adaptive + periodic topology refresh so stale nodes are re-discovered.
        val topologyRefresh = ClusterTopologyRefreshOptions.builder()
            .enablePeriodicRefresh(Duration.ofSeconds(30))
            .enableAllAdaptiveRefreshTriggers()
            .adaptiveRefreshTriggersTimeout(Duration.ofSeconds(30))
            .dynamicRefreshSources(true)
            .build()

        val clusterClientOptions = ClusterClientOptions.builder()
            .topologyRefreshOptions(topologyRefresh)
            // Tolerate transient membership gaps during a reschedule instead of rejecting nodes.
            .validateClusterNodeMembership(false)
            // k8s conntrack/LBs silently reap idle TCP flows; keep-alive detects dead sockets and
            // a bounded command timeout stops calls stalling for the 60s Lettuce default.
            .socketOptions(SocketOptions.builder().keepAlive(true).build())
            .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(5)))
            .build()

        val clientConfig = LettuceClientConfiguration.builder()
            .readFrom(ReadFrom.REPLICA_PREFERRED)
            .clientOptions(clusterClientOptions)
            .build()

        val factory = LettuceConnectionFactory(redisConfig, clientConfig)
        return factory
    }

    @Bean
    @ConditionalOnMissingBean(RedisClusterConfigurationProperties::class)
    fun lettuceStandaloneConnectionFactory(configurationProperties: RedisStandaloneConfigurationProperties): RedisConnectionFactory {
        val redisConfig = RedisStandaloneConfiguration(configurationProperties.host, configurationProperties.port)
        val factory = LettuceConnectionFactory(redisConfig)
        return factory
    }

    @Bean
    fun redisTemplate(
        redisConnectionFactory: RedisConnectionFactory,
        redisClusterConfigurationProperties: RedisClusterConfigurationProperties?
    ): RedisTemplate<String, Any> {
        val template: RedisTemplate<String, Any> = RedisTemplate()
        template.connectionFactory = redisConnectionFactory
        // The legacy GenericJackson2JsonRedisSerializer() default constructor enabled
        // permissive ("@class") default typing; preserve that behaviour explicitly.
        template.valueSerializer = GenericJacksonJsonRedisSerializer.builder()
            .enableUnsafeDefaultTyping()
            .build()

        redisClusterConfigurationProperties?.keyPrefix?.let { prefix ->
            template.keySerializer = object : StringRedisSerializer() {
                override fun serialize(key: String?): ByteArray {
                    return super.serialize(prefix + key)
                }
            }
        }

        return template
    }


}
