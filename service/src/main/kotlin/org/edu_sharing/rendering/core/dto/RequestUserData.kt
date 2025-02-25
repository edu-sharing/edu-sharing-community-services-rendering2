package org.edu_sharing.rendering.core.dto

data class RequestUserData(
    val authorityName: String,
    val firstName: String,
    val surName: String,
    val userEMail: String
)
