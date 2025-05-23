package org.edu_sharing.rendering.security.cors

import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.utils.cleanUrl
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig(
    private val appInfo: AppInfo,
    @Value("\${app.security.enabled}")
    private val securityEnabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(CorsConfig::class.java)

    private val allowedOrigins: MutableSet<String> = mutableSetOf()
    private val allowedPatterns: MutableSet<String> = mutableSetOf()

    private val source = UrlBasedCorsConfigurationSource()

    init {
        updateCorsConfiguration()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        return source
    }

    fun updateAllowedOrigins(allowedOrigins: Set<String>) {
        this.allowedOrigins.clear()
        this.allowedOrigins.addAll(allowedOrigins)
    }

    fun updateAllowedPatterns(allowedPatterns: Set<String>) {
        this.allowedPatterns.clear()
        this.allowedPatterns.addAll(allowedPatterns)
    }

    fun updateCorsConfiguration() {
        log.info("Creating new cors configuration.")
        val config = CorsConfiguration()
        log.debug(
            "Updating CORS configuration with allowed origins: {} and allowed patterns: {}",
            allowedOrigins,
            allowedPatterns
        )
        config.allowedOrigins = allowedOrigins.toList()
        config.allowedOriginPatterns = allowedPatterns.toList()
        log.debug("Adding own urls to cors configuration: {}, {}",
            appInfo.public.url.cleanUrl(),
            appInfo.internal.url.cleanUrl()
            )
        config.addAllowedOrigin(appInfo.public.url.cleanUrl())
        config.addAllowedOrigin(appInfo.internal.url.cleanUrl())
        config.addAllowedOrigin("http://localhost:11111")
        config.addAllowedOrigin("http://localhost:4200")
        config.allowCredentials = true
        config.allowedHeaders = listOf("Origin", "Content-Type", "Accept", "Authorization", "authorization", "Authentication-Info")
        config.allowedMethods = listOf("GET", "POST", "PUT", "OPTIONS", "DELETE", "PATCH")
        config.addExposedHeader("Access-Control-Allow-Origin")
        config.addExposedHeader("Authentication-Info")
        if (securityEnabled) {
            config.addExposedHeader("Access-Control-Allow-Credentials")
        }
        source.registerCorsConfiguration("/**", config)
        log.info("Created and registered new cors configuration.")
    }
}
