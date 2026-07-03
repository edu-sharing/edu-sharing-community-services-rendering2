package org.edu_sharing.rendering.modules.moodle

data class RestoreStatusDto(
    val status: String,
    val internalMessage: String? = null,
    val userMessage: String? = null,
    val courseId: Int? = null,
)
