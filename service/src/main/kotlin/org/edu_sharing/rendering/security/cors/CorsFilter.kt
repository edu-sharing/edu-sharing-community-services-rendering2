package org.edu_sharing.rendering.security.cors

/**
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class CorsFilter(
    @Value("\${app.security.allowedOriginPatterns}")
    private val allowedPatterns: List<String>,
    @Value("\${app.security.allowedOrigins}")
    private val allowedOrigins: List<String>
): OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val origin = request.getHeader("Origin")
        if (origin != null && (allowedOrigins.any { it == origin } || allowedPatterns.any { it.toRegex().matches(origin) })) {
            response.setHeader("Access-Control-Allow-Origin", origin)
            response.setHeader("Access-Control-Allow-Credentials", "true")
            response.setHeader(
                "Access-Control-Allow-Headers",
                "Origin, Content-Type, Accept, Authorization, Authentication-Info"
            )
            response.setHeader(
                "Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS, PATCH"
            )
            response.setHeader("Access-Control-Expose-Headers", "Authentication-Info")
        }

        if (request.method.equals("OPTIONS", ignoreCase = true)) {
            response.status = HttpServletResponse.SC_OK
        } else {
            filterChain.doFilter(request, response)
        }
    }
}

 */