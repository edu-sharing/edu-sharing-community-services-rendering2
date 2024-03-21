package org.edu_sharing.rendering.config

import org.edu_sharing.rendering.security.jwt.AuthTokenFilter
import org.edu_sharing.rendering.security.jwt.JwtPermissionEvaluator
import org.edu_sharing.rendering.security.jwt.JwtUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.*


@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Value("\${app.security.allowedOrigins}")
    lateinit var allowedOrigins: List<String>

    var publicKey =
        "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAprqEcRQVkWUkWfzMhrTtfK09P1hiARnyGRt9C+EvrZAmsRocdlN+6Bb4xpzz37pGkIsXy8sq/ZfbBpsc9Mz0f2YU5AJpdVq+lN9OLGGZc0+wH7UB3js+McbAhURmj3AYz8pBCAA2vvm4+GBXYZrfEOry9Lp6qkSGt+jvFTPRgf5pExnR5/24MLa5Kz9ATaoCcv1okeftddU2C+HC5HZ6NTe/uxRzSP05N/BhHqrSlBVocFjy8Ak6wzbaP4wtdZjVv+LqKTwRiTNJDPjxRv2bbsIDjSTVD6tlcaLsSbHBhHJCXLIMaO9jLcfV7yG0tcK6reI2QGxdlPo9lSkklEfqPwIDAQAB-----END PUBLIC KEY-----"

    @Bean
    fun jwtUtils(): JwtUtils {
        val publicKeyData =
            publicKey.replace("-----BEGIN PUBLIC KEY-----\n", "").replace("-----END PUBLIC KEY-----", "")
        val keySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyData))
        return JwtUtils(KeyFactory.getInstance("RSA").generatePublic(keySpec))
    }

    @Bean
    fun permissionEvaluator(): PermissionEvaluator {
        return JwtPermissionEvaluator()
    }

    @Bean
    fun expressionHandler() : MethodSecurityExpressionHandler {
        val defaultMethodSecurityExpressionHandler = DefaultMethodSecurityExpressionHandler()
        defaultMethodSecurityExpressionHandler.setPermissionEvaluator(permissionEvaluator())
        return defaultMethodSecurityExpressionHandler
    }

    @Bean
    fun authenticationJwtTokenFilter(jwtUtils: JwtUtils): AuthTokenFilter {
        return AuthTokenFilter(jwtUtils)
    }

    @Bean
    fun filterChain(httpSecurity: HttpSecurity): SecurityFilterChain {
        return httpSecurity.csrf {
            it.disable()
        }.cors {
            it.configurationSource(corsConfigurationSource())
        }.authorizeHttpRequests {
            it.requestMatchers(
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**"
            ).permitAll()
            it.anyRequest().authenticated()
        }.addFilterBefore(authenticationJwtTokenFilter(jwtUtils()), UsernamePasswordAuthenticationFilter::class.java)
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
