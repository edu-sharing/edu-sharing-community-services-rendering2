package org.edu_sharing.rendering.core

import jakarta.servlet.http.HttpServletRequest
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/public/session")
@ConditionalOnController
class SessionController {

    private val log = LoggerFactory.getLogger(javaClass)

    @DeleteMapping
    fun logout(request: HttpServletRequest): ResponseEntity<Void> {
        val session = request.getSession(false)
        if (session != null) {
            log.debug("DELETE /public/session invalidating session id=${session.id}")
            session.invalidate()
        } else {
            log.debug("DELETE /public/session no active session")
        }
        SecurityContextHolder.clearContext()
        return ResponseEntity.noContent().build()
    }
}
