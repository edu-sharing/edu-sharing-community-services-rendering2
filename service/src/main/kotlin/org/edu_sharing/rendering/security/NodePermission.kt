package org.edu_sharing.rendering.security


import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.time.LocalDateTime

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class NodePermission(var nodeId: String, var permissions: Set<String>, var lastAccessDate: LocalDateTime) {
    constructor() : this("", emptySet(), LocalDateTime.now())

    fun hasPermission(permission: String) : Boolean {
        lastAccessDate = LocalDateTime.now()
        return permissions.contains(permission)
    }
}