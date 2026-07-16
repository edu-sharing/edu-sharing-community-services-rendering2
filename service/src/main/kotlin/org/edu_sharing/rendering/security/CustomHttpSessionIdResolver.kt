package org.edu_sharing.rendering.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer
import org.springframework.boot.web.server.autoconfigure.ServerProperties
import org.springframework.boot.web.server.Cookie
import org.springframework.session.web.http.CookieHttpSessionIdResolver
import org.springframework.session.web.http.DefaultCookieSerializer
import org.springframework.session.web.http.HeaderHttpSessionIdResolver
import org.springframework.session.web.http.HttpSessionIdResolver
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Class CustomHttpSessionIdResolver
 *
 * This class replaces the standard cookie resolver to enable a two-fold strategy for session resolving.
 * The session can be read via cookie or header (X-Auth-Token).
 *
 * By default, the strategy favors the cookie over the header. If a client does not accept and/or send the cookie,
 * the header is used.
 */
@Component
class CustomHttpSessionIdResolver(
    private val serverProperties: ServerProperties,
    private val cookieSerializerCustomizers: ObjectProvider<DefaultCookieSerializerCustomizer>
) : HttpSessionIdResolver {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    private val cookieHttpSessionIdResolver = CookieHttpSessionIdResolver()
    private val headerHttpSessionIdResolver = HeaderHttpSessionIdResolver.authenticationInfo()

    init {
        val cookie: Cookie = serverProperties.servlet.session.cookie
        val cookieSerializer = DefaultCookieSerializer()
        // Boot 4's PropertyMapper.Source is now non-null (T : Any) and alwaysApplyingWhenNonNull()
        // was removed, so map the nullable cookie properties with plain null-safe Kotlin instead.
        cookie.name?.let { cookieSerializer.setCookieName(it) }
        cookie.domain?.let { cookieSerializer.setDomainName(it) }
        cookie.path?.let { cookieSerializer.setCookiePath(it) }
        cookie.httpOnly?.let { cookieSerializer.setUseHttpOnlyCookie(it) }
        cookie.secure?.let { cookieSerializer.setUseSecureCookie(it) }
        cookie.maxAge?.let { cookieSerializer.setCookieMaxAge(it.seconds.toInt()) }
        cookie.sameSite?.let { cookieSerializer.setSameSite(it.attributeValue()) }
        cookie.partitioned?.let { cookieSerializer.setPartitioned(it) }
        cookieSerializerCustomizers.orderedStream()
            .forEach { customizer: DefaultCookieSerializerCustomizer -> customizer.customize(cookieSerializer) }
        cookieHttpSessionIdResolver.setCookieSerializer(cookieSerializer)
    }

    override fun resolveSessionIds(request: HttpServletRequest?): List<String?>? {
        if (request != null) {
            log.debug("Resolving session id from request ${request.requestURI}")
        }
        val cookieSessionValues = cookieHttpSessionIdResolver.resolveSessionIds(request)
        if (cookieSessionValues.isNotEmpty()) {
            log.debug("Found session id in cookie")
            return cookieSessionValues
        }
        log.debug("No session id found in cookie")
        val headerSessionValues = headerHttpSessionIdResolver.resolveSessionIds(request)
        if (headerSessionValues.isNotEmpty()) {
            log.debug("Session id found in header")
        }
        return headerSessionValues
    }

    override fun setSessionId(
        request: HttpServletRequest?, response: HttpServletResponse?, sessionId: String?
    ) {
        cookieHttpSessionIdResolver.setSessionId(request, response, sessionId)
        headerHttpSessionIdResolver.setSessionId(request, response, sessionId)
    }

    override fun expireSession(
        request: HttpServletRequest?, response: HttpServletResponse?
    ) {
        cookieHttpSessionIdResolver.expireSession(request, response)
        headerHttpSessionIdResolver.expireSession(request, response)
    }
}
