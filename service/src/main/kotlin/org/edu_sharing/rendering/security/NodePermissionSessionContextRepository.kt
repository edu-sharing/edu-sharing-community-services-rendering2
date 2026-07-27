package org.edu_sharing.rendering.security

import jakarta.servlet.http.HttpSession
import org.edu_sharing.rendering.core.exception.ObjectTypeNotSupportedException
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
        val renderModule = try {
            renderModuleRegistry.getRenderModule<RenderModule>(node)
        } catch (e: ObjectTypeNotSupportedException) {
            log.debug("No render module for node '{}', using default node permission expiration time", nodePermission.nodeId)
            return nodePermissionExpirationTime
        }
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

        val result = findNodePermission(nodePermissions, nodeId)?.hasPermission(permission) == true
        log.debug("Permission check for nodeId=$nodeId, permission=$permission: ${if (result) "allowed" else "denied"}")
        return result
    }

    fun getNodePermission(nodeId: String): NodePermission? {
        val session = getSession(false) ?: return null
        val nodePermissions = readNodePermissionsFromSession(session) ?: return null

        return findNodePermission(nodePermissions, nodeId)
    }

    /**
     * Permissions are stored under the id the repository issued the JWT for — for collection
     * references that is the reference id, while the reference's assets and jobs are keyed by
     * the original node's id. On a direct miss, resolve the original id through the signed
     * reference node in the session and answer with the reference's permissions.
     */
    private fun findNodePermission(nodePermissions: List<NodePermission>, nodeId: String): NodePermission? {
        nodePermissions.firstOrNull { it.nodeId == nodeId }?.let { return it }
        val referenceNode = nodeSessionContextRepository.getNode(nodeId) ?: return null
        if (referenceNode.ref.id == nodeId) return null
        log.debug("Resolved nodeId=$nodeId to collection reference ${referenceNode.ref.id} for permission lookup")
        return nodePermissions.firstOrNull { it.nodeId == referenceNode.ref.id }
    }
}
