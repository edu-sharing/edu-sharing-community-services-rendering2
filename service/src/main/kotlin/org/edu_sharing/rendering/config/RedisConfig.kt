package org.edu_sharing.rendering.config

import io.lettuce.core.ReadFrom
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
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig {


    @Bean
    @ConditionalOnBean(RedisClusterConfigurationProperties::class)
    fun lettuceClusterConnectionFactory(configurationProperties: RedisClusterConfigurationProperties): RedisConnectionFactory {
        val redisConfig = RedisClusterConfiguration(configurationProperties.nodes)

        configurationProperties.maxRedirects?.let { redisConfig.setMaxRedirects(it) }
        val clientConfig = LettuceClientConfiguration.builder()
            .readFrom(ReadFrom.REPLICA_PREFERRED)
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
        template.valueSerializer = GenericJackson2JsonRedisSerializer()

        redisClusterConfigurationProperties?.keyPrefix?.let { prefix ->
            template.keySerializer = object : StringRedisSerializer() {
                override fun serialize(key: String?): ByteArray? {
                    return super.serialize(prefix + key)
                }
            }
        }

        return template
    }


}
