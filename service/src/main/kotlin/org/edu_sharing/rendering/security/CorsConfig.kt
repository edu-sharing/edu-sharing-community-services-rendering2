package org.edu_sharing.rendering.security

import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.utils.cleanUrl
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
    @Value("\${app.security.allowedOrigins}")
    private val allowedOriginsFromConfig: MutableList<String>
) {

    private val allowedOrigins: MutableList<String> = mutableListOf()

    init {
        init()
    }

    private final fun init(){
        allowedOrigins.addAll(allowedOriginsFromConfig)
        addAllowedOrigin(appInfo.public.url.cleanUrl())
        addAllowedOrigin(appInfo.internal.url.cleanUrl())
        addAllowedOrigin("http://localhost:4200")
        if (!securityEnabled) {
            addAllowedOrigin("http://localhost:4200")
            // local moodle
            //addAllowedOrigin("http://localhost:11111")
        }
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = if (securityEnabled) allowedOrigins else listOf("http://localhost:4200")
        config.allowCredentials = true
        config.allowedHeaders = listOf("Origin", "Content-Type", "Accept", "Authorization", "authorization", "Authentication-Info")
        config.allowedMethods = listOf("GET", "POST", "PUT", "OPTIONS", "DELETE", "PATCH")
        config.addExposedHeader("Access-Control-Allow-Origin")
        config.addExposedHeader("Authentication-Info")
        if (securityEnabled) {
            config.addExposedHeader("Access-Control-Allow-Credentials")
        }

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    final fun addAllowedOrigin(origin: String) {
        if (!allowedOrigins.contains(origin)) {
            allowedOrigins.add(origin)
        }
    }


    final fun removeAllowedOrigin(origin: String) {
        allowedOrigins.remove(origin)
    }

    final fun getAllowedOrigins(): List<String> = allowedOrigins

    final fun clearExternalOrigins(){
        allowedOrigins.clear()
        init()
    }
}
