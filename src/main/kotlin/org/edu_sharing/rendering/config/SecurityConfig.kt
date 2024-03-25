package org.edu_sharing.rendering.config

import org.edu_sharing.rendering.security.AuthTokenFilter
import org.edu_sharing.rendering.security.LocalPermissionStorage
import org.edu_sharing.rendering.security.LocalPermissionStorageEvaluator
import org.edu_sharing.rendering.security.jwt.JwtUtils
import org.edu_sharing.rendering.security.jwt.RepositoryPublicKeyProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationFilter
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource


@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Value("\${app.security.allowedOrigins}")
    lateinit var allowedOrigins: List<String>


    @Bean
    fun jwtUtils(publicKeyProvider: RepositoryPublicKeyProvider): JwtUtils {
        return JwtUtils(publicKeyProvider)
    }

    @Bean
    fun permissionEvaluator(localPermissionStorage: LocalPermissionStorage): PermissionEvaluator {
        return LocalPermissionStorageEvaluator(localPermissionStorage)
    }

    @Bean
    fun expressionHandler(permissionEvaluator: PermissionEvaluator) : MethodSecurityExpressionHandler {
        val defaultMethodSecurityExpressionHandler = DefaultMethodSecurityExpressionHandler()
        defaultMethodSecurityExpressionHandler.setPermissionEvaluator(permissionEvaluator)
        return defaultMethodSecurityExpressionHandler
    }

    @Bean
    fun securityContextRepository(): SecurityContextRepository {
        return HttpSessionSecurityContextRepository()
    }

    @Bean
    fun authenticationJwtTokenFilter(jwtUtils: JwtUtils, localPermissionStorage: LocalPermissionStorage, securityContextRepository:SecurityContextRepository): AuthTokenFilter {
        return AuthTokenFilter(jwtUtils, localPermissionStorage, securityContextRepository)
    }

    @Bean
    fun filterChain(httpSecurity: HttpSecurity, authenticationJwtTokenFilter: AuthTokenFilter): SecurityFilterChain {
        return httpSecurity.csrf {
            it.disable()
        }.cors {
            it.configurationSource(corsConfigurationSource())
        }.authorizeHttpRequests {
            it.requestMatchers(
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**",
                "/public/metadata"
            ).permitAll()
            it.anyRequest().authenticated()
        }.addFilterBefore(authenticationJwtTokenFilter, UsernamePasswordAuthenticationFilter::class.java)
//            .securityContext{
//                it.securityContextRepository()
//            }
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
