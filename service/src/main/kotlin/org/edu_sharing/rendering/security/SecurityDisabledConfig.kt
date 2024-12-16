package org.edu_sharing.rendering.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfigurationSource


@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name = ["app.security.enabled"], havingValue = "false")
class SecurityDisabledConfig {
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
        corsConfigurationSource:CorsConfigurationSource
    ): SecurityFilterChain {
        return httpSecurity
            .securityMatcher("/**")
            .csrf {
                it.disable()
            }.cors {
                it.configurationSource(corsConfigurationSource)
            }.authorizeHttpRequests {
                it.requestMatchers(
                    "/**"
                ).permitAll()
                it.anyRequest().authenticated()
            }
            .build()
    }
}
