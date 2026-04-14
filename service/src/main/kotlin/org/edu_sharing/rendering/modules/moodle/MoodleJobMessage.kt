package org.edu_sharing.rendering.modules.moodle

data class MoodleJobMessage(
    val id: String,
    val nodeId: String,
    val hash: String,
    val title: String,
    val userName: String,
    val userEmail: String,
    val firstName: String,
    val lastName: String,
)
