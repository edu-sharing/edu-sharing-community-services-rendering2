package org.edu_sharing.rendering.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.beans.factory.BeanClassLoaderAware
import org.springframework.boot.autoconfigure.session.DefaultCookieSerializerCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.security.jackson2.SecurityJackson2Modules
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession
import org.springframework.session.web.http.DefaultCookieSerializer

@Configuration
@EnableRedisIndexedHttpSession
class SessionConfig: BeanClassLoaderAware, DefaultCookieSerializerCustomizer {

    private lateinit var loader: ClassLoader

    @Bean
    fun springSessionDefaultRedisSerializer(): RedisSerializer<Any> {
        return GenericJackson2JsonRedisSerializer(objectMapper())
    }

    private fun objectMapper(): ObjectMapper {
        val mapper = ObjectMapper()
        mapper.registerModules(SecurityJackson2Modules.getModules(this.loader))
        mapper.registerModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.SingletonSupport, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
        mapper.registerModule(JavaTimeModule())
        mapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        mapper.activateDefaultTypingAsProperty(mapper.polymorphicTypeValidator, ObjectMapper.DefaultTyping.NON_FINAL, "@class")
        return mapper
    }

    override fun setBeanClassLoader(classLoader: ClassLoader) {
        this.loader = classLoader
    }

    override fun customize(cookieSerializer: DefaultCookieSerializer?) {
        println("customize function runs on ${if (cookieSerializer == null) "null" else "not null"}")
        cookieSerializer?.apply {
            setCookieName("SESSION_RS2")
            setDomainNamePattern("^.*?([^.]+\\.[^.]+)$")
            setSameSite("None")
            setUseSecureCookie(true)
        }
    }
}
