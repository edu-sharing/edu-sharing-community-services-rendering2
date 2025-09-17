package org.edu_sharing.rendering.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.session.web.http.CookieHttpSessionIdResolver
import org.springframework.session.web.http.HeaderHttpSessionIdResolver
import org.springframework.session.web.http.HttpSessionIdResolver
import org.springframework.stereotype.Component

/**
 * Class CustomHttpSessionIdResolver
 *
 * This class replaces the standard cookie resolver in order to enable a two-fold strategy for session resolving.
 * The session can be read via cookie or header (X-Auth-Token).
 *
 * By default, the strategy favors the cookie over the header. If a client does not accept and/or send the cookie,
 * the header is used.
 */
@Component
class CustomHttpSessionIdResolver: HttpSessionIdResolver {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    private val cookieHttpSessionIdResolver = CookieHttpSessionIdResolver()
    private val headerHttpSessionIdResolver = HeaderHttpSessionIdResolver.authenticationInfo()

    override fun resolveSessionIds(request: HttpServletRequest?): List<String?>? {
        val cookieSessionValues = cookieHttpSessionIdResolver.resolveSessionIds(request)
        if (cookieSessionValues.isNotEmpty()) {
            log.debug("Found session id in cookie")
            return cookieSessionValues
        }
        log.debug("No session id found in cookie")
        val headerSessionValues = headerHttpSessionIdResolver.resolveSessionIds(request)
        if (headerSessionValues.isNotEmpty()) {
            log.debug("No session id found in header")
        }
        return headerSessionValues
    }

    override fun setSessionId(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        sessionId: String?
    ) {
        cookieHttpSessionIdResolver.setSessionId(request, response, sessionId)
        headerHttpSessionIdResolver.setSessionId(request, response, sessionId)
    }

    override fun expireSession(
        request: HttpServletRequest?,
        response: HttpServletResponse?
    ) {
        cookieHttpSessionIdResolver.expireSession(request, response)
        headerHttpSessionIdResolver.expireSession(request, response)
    }
}
