package org.edu_sharing.rendering.dto

data class RequestUserData(
    val authorityName: String,
    val firstName: String?,
    val surName: String?,
    val userEMail: String?
)
