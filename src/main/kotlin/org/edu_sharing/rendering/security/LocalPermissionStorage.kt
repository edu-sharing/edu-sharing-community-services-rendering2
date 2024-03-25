package org.edu_sharing.rendering.security

import org.springframework.stereotype.Service

@Service
class LocalPermissionStorage {
    private val storage = ThreadLocal<MutableMap<String, NodePermission>>()

    fun storePermissions(nodePermission: NodePermission) {
        if(storage.get() == null){
            storage.set(HashMap())
        }
        storage.get()[nodePermission.nodeId] = nodePermission
    }

    fun storePermissions(nodePermissions: Collection<NodePermission>) {
        if(storage.get() == null){
            storage.set(HashMap())
        }
        nodePermissions.forEach{ storage.get()[it.nodeId] = it }
    }

    fun hasPermission(nodeId: String, permission: String): Boolean {
        return storage.get()?.get(nodeId)?.hasPermission(permission) ?: false
    }
}