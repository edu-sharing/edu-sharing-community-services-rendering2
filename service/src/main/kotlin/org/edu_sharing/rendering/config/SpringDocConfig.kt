package org.edu_sharing.rendering.config

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.security.SecuritySchemes
import org.springframework.context.annotation.Configuration

@Configuration
@SecuritySchemes(
    SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT"),
    SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic")
)
class SpringDocConfig {
}