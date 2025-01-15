package org.edu_sharing.rendering.security


import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.time.LocalDateTime

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class NodePermission(
    var nodeId: String,
    var permissions: Set<String>,
    var mimeType: String,
    var mediaType: String,
    var replicationSource: String?,
    var lastAccessDate: LocalDateTime
) {

    fun hasPermission(permission: String) : Boolean {
        lastAccessDate = LocalDateTime.now()
        return permissions.contains(permission)
    }
}
