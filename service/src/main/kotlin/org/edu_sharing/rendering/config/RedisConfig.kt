package org.edu_sharing.rendering.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisClusterConfiguration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer

@Configuration
class RedisConfig {


    @Bean
    @ConditionalOnBean(RedisClusterConfigurationProperties::class)
    fun lettuceClusterConnectionFactory(configurationProperties: RedisClusterConfigurationProperties): RedisConnectionFactory {
        val redisConfig = RedisClusterConfiguration(configurationProperties.nodes)
        val factory = LettuceConnectionFactory(redisConfig)
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
    fun redisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        val template: RedisTemplate<String, Any> = RedisTemplate()
        template.connectionFactory = redisConnectionFactory
        template.valueSerializer = GenericJackson2JsonRedisSerializer()
        return template
    }
}
