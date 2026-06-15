package org.edu_sharing.rendering.edusharingRepo

import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import org.springframework.stereotype.Component

/**
 * Stores the repository appAuth ticket (the `EDU-TICKET`) inside the rs2 Spring Session,
 * keyed by repository. Unlike [org.edu_sharing.rendering.security.NodeSessionContextRepository]
 * this accesses the session through the [SessionRepository] bean by a captured session id, so it
 * is safe to call from the OkHttp background thread that runs `trackEventAsync` (where there is no
 * thread-bound request context). Spring Session persists attribute changes as deltas, so writing
 * the ticket attribute off-thread does not clobber the servlet's own session commit.
 */
@Component
@ConditionalOnController
class SessionTicketRepository(
    private val sessionRepository: SessionRepository<out Session>
) {

    fun getTicket(sessionId: String, repoId: String): String? =
        sessionRepository.findById(sessionId)?.getAttribute(attribute(repoId))

    fun saveTicket(sessionId: String, repoId: String, ticket: String) {
        val session = sessionRepository.findById(sessionId) ?: return
        session.setAttribute(attribute(repoId), ticket)
        save(session)
    }

    fun invalidate(sessionId: String, repoId: String) {
        val session = sessionRepository.findById(sessionId) ?: return
        session.removeAttribute(attribute(repoId))
        save(session)
    }

    private fun attribute(repoId: String) = "$TICKET_ATTRIBUTE_PREFIX$repoId"

    @Suppress("UNCHECKED_CAST")
    private fun save(session: Session) {
        // findById returned this session from the same repository, so the runtime type matches.
        (sessionRepository as SessionRepository<Session>).save(session)
    }

    companion object {
        const val TICKET_ATTRIBUTE_PREFIX = "edu-ticket:"
    }
}
