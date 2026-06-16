package org.edu_sharing.rendering.security

import jakarta.servlet.http.HttpSession
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.LocalDateTime

private const val PERMISSIONS = "permissions"

@Component
class NodePermissionSessionContextRepository(
    @Value($$"${app.session.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long,
    private val renderModuleRegistry: ModuleRegistry,
    private val nodeSessionContextRepository: NodeSessionContextRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun getExpirationTime(nodePermission: NodePermission): Long {
        val node = nodeSessionContextRepository.getNode(nodePermission.nodeId) ?: return nodePermissionExpirationTime
        val renderModule = renderModuleRegistry.getRenderModule<RenderModule>(node)
        return renderModule.getNodePermissionExpirationTime() ?: nodePermissionExpirationTime
    }

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
        val allExpiredNodeIds = nodePermissions.filter { now.isAfter(it.lastAccessDate.plusSeconds(getExpirationTime(it))) }.map { it.nodeId }
        if (allExpiredNodeIds.isNotEmpty()) {
            log.debug("Expiring ${allExpiredNodeIds.size} node permission(s) from session: $allExpiredNodeIds")
        }
        nodePermissions.removeAll { it.nodeId in allExpiredNodeIds }
        nodeSessionContextRepository.removeAll(allExpiredNodeIds)
        if (nodePermissions.isEmpty()) {
            session.removeAttribute(PERMISSIONS)
        } else {
            session.setAttribute(PERMISSIONS, nodePermissions)
        }
    }

    fun saveNodePermission(nodePermission: NodePermission) {
        val session = getSession(true) ?: return
        val nodePermissions = readNodePermissionsFromSession(session) ?: mutableListOf()
        nodePermissions.removeAll { it.nodeId == nodePermission.nodeId && it.repoId == nodePermission.repoId }
        nodePermissions.add(nodePermission)
        session.setAttribute(PERMISSIONS, nodePermissions)
    }

    fun hasPermission(nodeId: String, permission: String): Boolean {
        val session = getSession(false) ?: run {
            log.debug("Permission check for nodeId=$nodeId: denied (no session)")
            return false
        }
        val nodePermissions = readNodePermissionsFromSession(session) ?: run {
            log.debug("Permission check for nodeId=$nodeId: denied (no permissions in session)")
            return false
        }

        val result = nodePermissions.firstOrNull { it.nodeId == nodeId }?.hasPermission(permission) == true
        log.debug("Permission check for nodeId=$nodeId, permission=$permission: ${if (result) "allowed" else "denied"}")
        return result
    }

    fun getNodePermission(nodeId: String): NodePermission? {
        val session = getSession(false) ?: return null
        val nodePermissions = readNodePermissionsFromSession(session) ?: return null

        return nodePermissions.find { it.nodeId == nodeId}
    }
}
