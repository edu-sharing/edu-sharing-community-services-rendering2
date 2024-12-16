package org.edu_sharing.rendering.edusharingRepo.dto

data class RegistrationInfo(
    val repoId: String,
    var url: String,
    var publicKey: String,
    var domains: List<String>
)
