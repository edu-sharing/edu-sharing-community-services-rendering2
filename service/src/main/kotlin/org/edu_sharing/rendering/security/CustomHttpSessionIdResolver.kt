package org.edu_sharing.rendering.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.session.DefaultCookieSerializerCustomizer
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.boot.context.properties.PropertyMapper
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
    private val cookieSerializerCustomizers: ObjectProvider<DefaultCookieSerializerCustomizer?>
) : HttpSessionIdResolver {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    private val cookieHttpSessionIdResolver = CookieHttpSessionIdResolver()
    private val headerHttpSessionIdResolver = HeaderHttpSessionIdResolver.authenticationInfo()

    init {
        val cookie: Cookie = serverProperties.servlet.session.cookie
        val cookieSerializer = DefaultCookieSerializer()
        val map = PropertyMapper.get().alwaysApplyingWhenNonNull()
        map.from<String?> { cookie.name }.to { cookieName: String? -> cookieSerializer.setCookieName(cookieName) }
        map.from<String?> { cookie.domain }.to { domainName: String? -> cookieSerializer.setDomainName(domainName) }
        map.from<String?> { cookie.path }.to { cookiePath: String? -> cookieSerializer.setCookiePath(cookiePath) }
        map.from<Boolean?> { cookie.httpOnly }
            .to { useHttpOnlyCookie: Boolean? -> cookieSerializer.setUseHttpOnlyCookie(useHttpOnlyCookie!!) }
        map.from<Boolean?> { cookie.secure }
            .to { useSecureCookie: Boolean? -> cookieSerializer.setUseSecureCookie(useSecureCookie!!) }
        map.from<Duration?> { cookie.maxAge }.asInt<Long?> { obj: Duration? -> obj!!.seconds }
            .to { cookieMaxAge: Int? -> cookieSerializer.setCookieMaxAge(cookieMaxAge!!) }
        map.from<Cookie.SameSite?> { cookie.sameSite }.`as`<String?> { obj: Cookie.SameSite? -> obj!!.attributeValue() }
            .to { sameSite: String? -> cookieSerializer.setSameSite(sameSite) }
        map.from<Boolean?> { cookie.partitioned }
            .to { partitioned: Boolean? -> cookieSerializer.setPartitioned(partitioned!!) }
        cookieSerializerCustomizers.orderedStream()
            .forEach { customizer: DefaultCookieSerializerCustomizer? -> customizer!!.customize(cookieSerializer) }
        cookieHttpSessionIdResolver.setCookieSerializer(cookieSerializer)
    }

    override fun resolveSessionIds(request: HttpServletRequest?): List<String?>? {
        if (request != null) {
            log.info("Resolving session id from request ${request.requestURI}")
        }
        val cookieSessionValues = cookieHttpSessionIdResolver.resolveSessionIds(request)
        if (cookieSessionValues.isNotEmpty()) {
            log.info("Found session id in cookie")
            return cookieSessionValues
        }
        log.info("No session id found in cookie")
        val headerSessionValues = headerHttpSessionIdResolver.resolveSessionIds(request)
        if (headerSessionValues.isNotEmpty()) {
            log.info("Session id found in header")
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
