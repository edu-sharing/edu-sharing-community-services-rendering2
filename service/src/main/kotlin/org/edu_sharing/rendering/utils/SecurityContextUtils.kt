package org.edu_sharing.rendering.utils

import org.edu_sharing.rendering.security.jwt.JWTBasedUserDetail
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

class SecurityContextUtils {

    companion object {

        /** The authenticated user of the current security context. */
        fun currentUser(): JWTBasedUserDetail =
            SecurityContextHolder.getContext().authentication!!.principal as JWTBasedUserDetail

        /** The authenticated user's id (JWT issuer) of the current security context. */
        fun currentUserId(): String = currentUser().username

        /**
         * Id of the current rs2 HTTP session, or `null` if there is none. Must be read on the request
         * thread; capture it before handing work to a background thread, where the request context is
         * no longer available.
         */
        fun currentSessionId(): String? =
            (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)
                ?.request?.getSession(false)?.id
    }
}
