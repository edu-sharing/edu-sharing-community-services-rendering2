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
    @Value($$"${app.security.enabled}")
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

    /**
     * The origin a browser sends in its `Origin` header for this endpoint: `protocol://host[:port]`, without the
     * context path and without the default port of the protocol. CORS compares the header with the allowed origins
     * as a plain string, so an own origin built from the full url (which carries the context path) never matches.
     */
    private fun AppInfo.ConnectionInfo.origin(): String = "$protocol://$host:$port".cleanUrl()

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
        val ownOrigins = listOf(appInfo.public.origin(), appInfo.internal.origin())
        log.debug("Adding own origins to cors configuration: {}", ownOrigins)
        ownOrigins.forEach { config.addAllowedOrigin(it) }
        config.allowCredentials = true
        config.allowedHeaders = listOf("Origin", "Content-Type", "Accept", "Authorization", "authorization", "Authentication-Info", "X-Client-Trace-Id")
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
