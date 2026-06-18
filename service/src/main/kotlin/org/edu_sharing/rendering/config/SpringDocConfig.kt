package org.edu_sharing.rendering.config

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.security.SecuritySchemes
import org.springdoc.core.providers.ObjectMapperProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@SecuritySchemes(
    SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT"),
    SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic")
)
class SpringDocConfig {

    /**
     * Overrides springdoc's default [ModelResolver] with one whose (Jackson 2) ObjectMapper has
     * the Kotlin module registered. swagger-core then sees Kotlin non-nullability and marks
     * non-null DTO properties as `required` in the generated OpenAPI schema (nullable `?` stay
     * optional). Without this, every property would be optional and the generated frontend
     * client would type all fields as `T | undefined`.
     */
    @Bean
    fun modelResolver(objectMapperProvider: ObjectMapperProvider): ModelResolver =
        ModelResolver(objectMapperProvider.jsonMapper().copy().registerKotlinModule())
}