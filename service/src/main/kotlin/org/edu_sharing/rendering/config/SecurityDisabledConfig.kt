package org.edu_sharing.rendering.config

import org.edu_sharing.rendering.security.AllowAllPermissionEvaluator
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource


@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name = ["app.security.enabled"], havingValue = "false")
class SecurityDisabledConfig(
    @Value("\${app.security.allowedOrigins}") var allowedOrigins: List<String>,
) {
    @Bean
    fun permissionEvaluator(): PermissionEvaluator {
        return AllowAllPermissionEvaluator()
    }

    @Bean
    fun expressionHandler(permissionEvaluator: PermissionEvaluator): MethodSecurityExpressionHandler {
        val defaultMethodSecurityExpressionHandler = DefaultMethodSecurityExpressionHandler()
        defaultMethodSecurityExpressionHandler.setPermissionEvaluator(permissionEvaluator)
        return defaultMethodSecurityExpressionHandler
    }

    @Bean
    fun filterChain(
        httpSecurity: HttpSecurity,
    ): SecurityFilterChain {
        return httpSecurity
            .securityMatcher("/**")
            .csrf {
                it.disable()
            }.cors {
                it.configurationSource(corsConfigurationSource())
            }.authorizeHttpRequests {
                it.requestMatchers(
                    "/**"
                ).permitAll()
                it.anyRequest().authenticated()
            }
            .build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowCredentials = true
        config.setAllowedOriginPatterns(allowedOrigins)
        config.allowedHeaders = listOf("Origin", "Content-Type", "Accept", "Authorization", "authorization")
        //config.setAllowedHeaders(Arrays.asList("Origin", "Content-Type", "Accept", "Authorization", "authorization", "x-requested-with"));
        //config.setAllowedHeaders(Arrays.asList("Origin", "Content-Type", "Accept", "Authorization", "authorization", "x-requested-with"));
        config.allowedMethods = listOf("GET", "POST", "PUT", "OPTIONS", "DELETE", "PATCH")
        config.addExposedHeader("Access-Control-Allow-Origin")
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
