package org.edu_sharing.rendering.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource


@Configuration
class SecurityConfig {
    @Value("\${app.security.allowedOrigins}")
    lateinit var allowedOrigins: List<String>
    @Bean
    fun filterChain(httpSecurity: HttpSecurity): SecurityFilterChain {
        return httpSecurity.csrf{
            it.disable()
        }.cors{
            it.configurationSource(corsConfigurationSource())
        }.authorizeHttpRequests{
            it.anyRequest().permitAll()
        }.build()
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
