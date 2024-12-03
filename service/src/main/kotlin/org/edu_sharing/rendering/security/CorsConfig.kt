package org.edu_sharing.rendering.security

import org.edu_sharing.rendering.config.AppInfo
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig(appInfo: AppInfo) {
    private final val allowedOrigins = mutableListOf<String>()

    init {
        addAllowedOrigin("${appInfo.public.host}:${appInfo.public.port}")
        addAllowedOrigin("${appInfo.internal.host}:${appInfo.internal.port}")
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = allowedOrigins
        config.allowedHeaders = listOf("Origin", "Content-Type", "Accept", "Authorization", "authorization")
        config.allowedMethods = listOf("GET", "POST", "PUT", "OPTIONS", "DELETE", "PATCH")
        config.addExposedHeader("Access-Control-Allow-Origin")

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
}
