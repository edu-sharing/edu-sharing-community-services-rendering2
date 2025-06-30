package org.edu_sharing.rendering.modules.moodle

data class MoodleJobMessage(
    val id: String,
    val nodeId: String,
    val hash: String,
    val title: String,
    val authorityName: String,
    val userEmail: String,
    val userGivenName: String,
    val userSurname: String
)
