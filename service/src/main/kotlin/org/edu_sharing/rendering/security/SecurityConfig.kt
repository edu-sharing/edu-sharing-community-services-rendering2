package org.edu_sharing.rendering.security

import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import org.edu_sharing.rendering.security.jwt.JwtUtils
import org.edu_sharing.rendering.edusharingRepo.services.PrivatePublicKeyService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource


@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name = ["app.security.enabled"], havingValue = "true")
class SecurityConfig(
    @Value("\${app.security.allowedOrigins}") var allowedOrigins: List<String>,
    @Value("\${app.security.adminPassword}") var adminPassword: String
) {

    @Bean
    fun jwtParser(keyService: PrivatePublicKeyService): JwtParser {
        return Jwts.parser()
            .verifyWith(keyService.getRepositoryKey())
            .build()
    }

    @Bean
    fun jwtUtils(jwtParser: JwtParser): JwtUtils {
        return JwtUtils(jwtParser)
    }

    @Bean
    fun permissionEvaluator(nodePermissionSessionContextRepository: NodePermissionSessionContextRepository): PermissionEvaluator {
        return NodePermissionSessionContextEvaluator(nodePermissionSessionContextRepository)
    }

    @Bean
    fun expressionHandler(permissionEvaluator: PermissionEvaluator): MethodSecurityExpressionHandler {
        val defaultMethodSecurityExpressionHandler = DefaultMethodSecurityExpressionHandler()
        defaultMethodSecurityExpressionHandler.setPermissionEvaluator(permissionEvaluator)
        return defaultMethodSecurityExpressionHandler
    }

    @Bean
    fun securityContextRepository(): SecurityContextRepository {
        return HttpSessionSecurityContextRepository()
    }

    @Bean
    fun authenticationJwtTokenFilter(
        jwtUtils: JwtUtils,
        nodePermissionSessionContextRepository: NodePermissionSessionContextRepository,
        securityContextRepository: SecurityContextRepository
    ): AuthTokenFilter {
        return AuthTokenFilter(jwtUtils, securityContextRepository, nodePermissionSessionContextRepository)
    }


    @Bean
    @Order(1)
    fun publicAPIFilterChain(
        httpSecurity: HttpSecurity,
        authenticationJwtTokenFilter: AuthTokenFilter
    ): SecurityFilterChain {
        return httpSecurity
            .securityMatcher("/public/**")
            .csrf {
                it.disable()
            }.cors {
                it.configurationSource(corsConfigurationSource())
            }.authorizeHttpRequests {
                it.requestMatchers(
                    "/public/metadata"
                ).permitAll()
                it.anyRequest().authenticated()
            }.addFilterBefore(authenticationJwtTokenFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    @Order(2)
    fun privateAPIFilterChain(httpSecurity: HttpSecurity): SecurityFilterChain {
        return httpSecurity
            .csrf {
                it.disable()
            }.cors {
                it.configurationSource(corsConfigurationSource())
            }.authorizeHttpRequests {
                it.requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                ).permitAll()
                it.anyRequest().authenticated()
            }.httpBasic(Customizer.withDefaults())
            .build()
    }


    @Bean
    fun authenticationProvider(): AuthenticationProvider {
        val authenticationProvider = DaoAuthenticationProvider()
        authenticationProvider.setUserDetailsService(userDetailsService())
        return authenticationProvider
    }

    @Bean
    fun userDetailsService(): InMemoryUserDetailsManager {
        val user: UserDetails = User.withUsername("admin")
            .password(adminPassword)
            .passwordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder()::encode)
            .roles("ADMIN")
            .build()
        return InMemoryUserDetailsManager(user)
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
