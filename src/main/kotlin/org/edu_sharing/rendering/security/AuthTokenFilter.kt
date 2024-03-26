package org.edu_sharing.rendering.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.edu_sharing.rendering.security.jwt.JWTBasedUserDetail
import org.edu_sharing.rendering.security.jwt.JwtUtils
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.util.StringUtils
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.time.LocalDateTime

private const val PERMISSIONS = "permissions"

class AuthTokenFilter(
    private var jwtUtils: JwtUtils,
    private var localPermissionRepository: LocalPermissionStorage,
    private var securityContextRepository: SecurityContextRepository
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("UNCHECKED_CAST")
    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {

        val session = request.session
        var permissionObject = session.getAttribute(PERMISSIONS)
        if (permissionObject == null) {
            permissionObject = ArrayList<NodePermission>()
        } else if (permissionObject !is List<*>) {
            log.warn("Session permissions object isn't of type List<>")
            permissionObject = ArrayList<NodePermission>()
        }

        // remove all invalid permissions
        var nodePermissions = permissionObject as List<NodePermission>
        val now = LocalDateTime.now()
        nodePermissions = ArrayList(nodePermissions.filter { now.isBefore(it.lastAccessDate.plusMinutes(1)) }) // TODO

        try {
            val jwt: String? = parseJwt(request)
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                val  userDetails = jwtUtils.getUserDetailsFromJwt(jwt)
                val authentication = UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.authorities
                )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authentication
                securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);

                val nodePermission = jwtUtils.getNodePermissions(jwt)
                nodePermissions.addLast(nodePermission)
            }
        } catch (ex: Exception) {
            log.error("Cannot set user authentication", ex)
        }

        if(nodePermissions.isNotEmpty()) {
            localPermissionRepository.storePermissions(nodePermissions)
            session.setAttribute(PERMISSIONS, nodePermissions)
        }else{
            session.removeAttribute(PERMISSIONS)
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