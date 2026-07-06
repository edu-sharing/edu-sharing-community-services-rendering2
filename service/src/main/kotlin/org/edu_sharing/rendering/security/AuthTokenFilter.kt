package org.edu_sharing.rendering.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.edu_sharing.rendering.security.jwt.JwtUtils
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.util.StringUtils
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

class AuthTokenFilter(
    private var jwtUtils: JwtUtils,
    private var securityContextRepository: SecurityContextRepository,
    private var nodePermissionSessionContextRepository: NodePermissionSessionContextRepository
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            nodePermissionSessionContextRepository.validateSessionPermissions()
        } catch (ex: Exception) {
            log.error("Cannot validate session permissions", ex)
        }
        try {
            val jwt: String? = parseJwt(request)
            if (jwt == null) {
                log.debug("No JWT token found in request to ${request.requestURI}")
            } else if (jwtUtils.validateJwtToken(jwt)) {
                val userDetails = jwtUtils.getUserDetailsFromJwt(jwt)
                log.debug("JWT token valid: subject=${userDetails.username}, repoId=${userDetails.repoId}")
                val authentication = UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.authorities
                )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authentication
                securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response)
                val nodePermission = jwtUtils.getNodePermissions(jwt)
                log.debug("Node permissions saved: nodeId=${nodePermission.nodeId}, repoId=${nodePermission.repoId}")
                nodePermissionSessionContextRepository.saveNodePermission(nodePermission)
            } else {
                log.debug("JWT token validation failed for request to ${request.requestURI}")
            }
        } catch (ex: Exception) {
            log.error("Cannot set user authentication", ex)
        }
        
        filterChain.doFilter(request, response)
    }

    private fun parseJwt(request: HttpServletRequest): String? {
        val headerAuth = request.getHeader("Authorization")
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7)
        }
        return null
    }

}
