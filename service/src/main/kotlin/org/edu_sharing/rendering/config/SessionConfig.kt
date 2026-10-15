package org.edu_sharing.rendering.config

import org.springframework.beans.factory.BeanClassLoaderAware
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.security.jackson.SecurityJacksonModules
import org.springframework.session.data.redis.config.ConfigureRedisAction
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

@Configuration
@EnableRedisIndexedHttpSession
@EnableConfigurationProperties(SessionRedisProperties::class)
class SessionConfig: BeanClassLoaderAware {

    private lateinit var loader: ClassLoader

    /**
     * Opt-out of Spring Session's startup `CONFIG SET notify-keyspace-events` for managed Redis that
     * forbids `CONFIG` (`app.redis.session.configure-action=NO_OP`; the server must then already have
     * `notify-keyspace-events Egx`). Spring Session picks up a [ConfigureRedisAction] bean; without
     * this bean its default action runs.
     */
    @Bean
    @ConditionalOnProperty(name = ["app.redis.session.configure-action"], havingValue = "NO_OP")
    fun configureRedisAction(): ConfigureRedisAction = ConfigureRedisAction.NO_OP

    @Bean
    fun springSessionDefaultRedisSerializer(): RedisSerializer<Any> {
        // GenericJacksonJsonRedisSerializer owns the default-typing setup (type property + validator),
        // so it must be configured through the builder; a mapper passed in with its own default typing
        // is overridden. enableUnsafeDefaultTyping() reproduces the permissive behaviour of the former
        // GenericJackson2JsonRedisSerializer (required so session values like Long/Duration round-trip).
        // The mapper itself is customized to register the Spring Security and Kotlin Jackson modules.
        // java.time support is built into jackson-databind in Jackson 3, so no explicit JavaTimeModule.
        //
        // SecurityJacksonModules.getModules(loader) does more than register mix-ins in Spring Security 7
        // (Jackson 3): it activates default typing with a BasicPolymorphicTypeValidator that allow-lists
        // only Spring Security types, and — because the customizer runs after the builder's own
        // enableUnsafeDefaultTyping() — that restrictive validator wins. We therefore seed it with a
        // permissive builder so session values (Long, the generated Node, the SecurityContext, ...) still
        // round-trip; without it deserialization fails with "denied resolution" for e.g. java.lang.Long.
        return GenericJacksonJsonRedisSerializer.builder()
            .customize { mapperBuilder ->
                mapperBuilder
                    .addModules(
                        SecurityJacksonModules.getModules(
                            this.loader,
                            BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType(Any::class.java)
                                .allowIfSubType { _, _ -> true }
                        )
                    )
                    .addModule(
                        KotlinModule.Builder()
                            .withReflectionCacheSize(512)
                            .configure(KotlinFeature.NullToEmptyCollection, false)
                            .configure(KotlinFeature.NullToEmptyMap, false)
                            .configure(KotlinFeature.NullIsSameAsDefault, false)
                            .configure(KotlinFeature.SingletonSupport, false)
                            .configure(KotlinFeature.StrictNullChecks, false)
                            .build()
                    )
                    .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
            .enableUnsafeDefaultTyping()
            .build()
    }

    override fun setBeanClassLoader(classLoader: ClassLoader) {
        this.loader = classLoader
    }
}
