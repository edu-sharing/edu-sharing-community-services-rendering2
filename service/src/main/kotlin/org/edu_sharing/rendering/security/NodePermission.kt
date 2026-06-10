package org.edu_sharing.rendering.security


import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.annotation.JsonSerialize
import java.time.LocalDateTime

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class NodePermission(
    var repoId: String,
    var nodeId: String,
    var permissions: Set<String>,
    var lastAccessDate: LocalDateTime
) {

    fun hasPermission(permission: String) : Boolean {
        lastAccessDate = LocalDateTime.now()
        return permissions.contains(permission)
    }
}
