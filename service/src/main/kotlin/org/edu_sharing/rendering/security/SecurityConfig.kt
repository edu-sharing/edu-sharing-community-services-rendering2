package org.edu_sharing.rendering.security

import org.edu_sharing.rendering.security.jwt.JwtUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.cors.CorsConfigurationSource


@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name = ["app.security.enabled"], havingValue = "true")
class SecurityConfig(
    @param:Value($$"${app.security.adminPassword}") var adminPassword: String
) {

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
        authenticationJwtTokenFilter: AuthTokenFilter,
        corsConfigurationSource: CorsConfigurationSource
    ): SecurityFilterChain {
        return httpSecurity
            .securityMatcher("/public/**")
            .csrf {
                it.disable()
            }.cors {
                it.configurationSource(corsConfigurationSource)
            }.headers { headers ->
                headers.frameOptions { it.disable() }
            }.authorizeHttpRequests {
                it.requestMatchers(
                    "/public/modules",
                    "/public/session"
                ).permitAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .addFilterBefore(authenticationJwtTokenFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    @Order(2)
    fun privateAPIFilterChain(
        httpSecurity: HttpSecurity,
        corsConfigurationSource: CorsConfigurationSource
    ): SecurityFilterChain {
        return httpSecurity
            .csrf {
                it.disable()
            }.cors {
                it.configurationSource(corsConfigurationSource)
            }.authorizeHttpRequests {
                it.requestMatchers("/v3/api-docs/administration/**").hasRole("ADMIN")
                it.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                it.requestMatchers("/actuator/health/*", "/actuator/prometheus", "/ping").permitAll()
                it.anyRequest().authenticated()
            }
            // Bei 401 nur den Status zurückgeben, KEIN `WWW-Authenticate: Basic` (wie der
            // public-Chain): sonst löst der Browser bei jedem 401 auf /admin seinen nativen
            // Login-Dialog aus. Das Admin-Frontend behandelt die Anmeldung selbst (Login-Seite).
            .httpBasic { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .build()
    }


    @Bean
    fun authenticationProvider(): AuthenticationProvider {
        return DaoAuthenticationProvider(userDetailsService())
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
}
