package org.edu_sharing.rendering.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer

@Configuration
class RedisConfig {

    @Value("\${edu_sharing.redis_url}")
    lateinit var redisUrl: String

    @Value("\${edu_sharing.redis_port}")
    lateinit var redisPort: String
    @Bean
    fun jedisConnectionFactory(): JedisConnectionFactory {
        val redisConfig = RedisStandaloneConfiguration(redisUrl, redisPort.toInt())
        val factory = JedisConnectionFactory(redisConfig)
        return factory
    }

    @Bean
    fun redisTemplate(jedisConnectionFactory: JedisConnectionFactory): RedisTemplate<String, Any> {
        val template: RedisTemplate<String, Any> = RedisTemplate()
        template.connectionFactory = jedisConnectionFactory
        template.valueSerializer = GenericJackson2JsonRedisSerializer()
        return template
    }
}