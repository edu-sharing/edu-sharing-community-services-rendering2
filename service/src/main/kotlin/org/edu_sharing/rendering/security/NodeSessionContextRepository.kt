package org.edu_sharing.rendering.security

import jakarta.servlet.http.HttpSession
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Component
class NodeSessionContextRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val NODES = "nodes"
    }

    private fun getSession(createSession: Boolean): HttpSession? {
        return (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request.getSession(
            createSession
        )
    }

    fun saveNode(node: Node) {
        val session = getSession(true) ?: return
        val nodes = readNodesFromSession(session) ?: mutableListOf()
        nodes.removeAll { it.ref.id == node.ref.id }
        nodes.add(node)
        session.setAttribute(NODES, nodes)
    }

    @Suppress("UNCHECKED_CAST")
    private fun readNodesFromSession(session: HttpSession?): MutableList<Node>? {
        val nodesFromSession = session?.getAttribute(NODES) ?: return null

        if (nodesFromSession !is MutableList<*> || !nodesFromSession.all { it is Node }) {
            log.warn(
                "{} did not contain valid nodes but contained: '{}'; are you improperly modifying the HttpSession directly (you should always use NodeSessionContextRepository) or using the HttpSession attribute reserved for this class?",
                NODES,
                nodesFromSession
            )
            return null
        }

        return nodesFromSession as MutableList<Node>
    }

    fun getNode(nodeId: String): Node? {
        val session = getSession(false) ?: return null
        val nodes = readNodesFromSession(session) ?: return null

        val node = nodes.firstOrNull { it.ref.id == nodeId }
        log.debug("Node session cache ${if (node != null) "hit" else "miss"} for nodeId=$nodeId")
        return node
    }

    fun removeAll(nodeIds: List<String>) {
        val session = getSession(false) ?: return
        val nodes = readNodesFromSession(session) ?: mutableListOf()
        nodes.removeAll { it.ref.id in nodeIds }
        if (nodes.isEmpty()) {
            session.removeAttribute(NODES)
        } else {
            session.setAttribute(NODES, nodes)
        }
    }
}