package org.edu_sharing.rendering.security

import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.LocalDateTime

private const val PERMISSIONS = "permissions"

@Component
class NodePermissionSessionContextRepository(
    @Value("\${app.session.nodePermissionExpirationTime}")
    private var nodePermissionExprationTime: Long
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun getSession(createSession: Boolean): HttpSession? {
        return (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request.getSession(
            createSession
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun readNodePermissionsFromSession(session: HttpSession?): MutableList<NodePermission>? {
        val permissionsFromSession = session?.getAttribute(PERMISSIONS) ?: return null

        if (permissionsFromSession !is MutableList<*> || !permissionsFromSession.all { it is NodePermission }) {
            log.warn(
                "{} did not contain a SecurityContext but contained: '{}'; are you improperly modifying the HttpSession directly (you should always use NodePermissionSessionContextRepository) or using the HttpSession attribute reserved for this class?",
                PERMISSIONS,
                permissionsFromSession
            )
            return null
        }

        return permissionsFromSession as MutableList<NodePermission>
    }

    /**
     * removes all nodePermissions from the session object that aren't valid anymore caused by lifetime
     */
    fun validateSessionPermissions() {
        val session = getSession(false) ?: return
        val nodePermissions = readNodePermissionsFromSession(session) ?: return
        val now = LocalDateTime.now()
        nodePermissions.removeAll { now.isAfter(it.lastAccessDate.plusSeconds(nodePermissionExprationTime)) }
        if (nodePermissions.isEmpty()) {
            session.removeAttribute(PERMISSIONS)
        } else {
            session.setAttribute(PERMISSIONS, nodePermissions)
        }
    }

    fun saveNodePermission(nodePermission: NodePermission) {
        val session = getSession(true) ?: return
        val nodePermissions = readNodePermissionsFromSession(session) ?: ArrayList()
        nodePermissions.removeAll { it.nodeId == nodePermission.nodeId }
        nodePermissions.addLast(nodePermission)
        session.setAttribute(PERMISSIONS, nodePermissions)
    }

    fun hasPermission(nodeId: String, permission: String): Boolean {
        val session = getSession(false) ?: return false
        val nodePermissions = readNodePermissionsFromSession(session) ?: return false

        return nodePermissions.firstOrNull { it.nodeId == nodeId }?.hasPermission(permission) ?: false
    }
}