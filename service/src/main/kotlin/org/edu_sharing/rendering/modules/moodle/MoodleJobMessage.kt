package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.config.annotation.ConditionalOnMoodle

@ConditionalOnMoodle
data class MoodleJobMessage(
    val id: String,
    val nodeId: String,
    val title: String,
    val authorityName: String,
    val userEmail: String? = null,
    val userGivenName: String? = null,
    val userSurname: String? = null
)
