package org.edu_sharing.rendering.modules.binder.dto

data class GitDetails(
    val user: String,
    val repo: String,
    val branch: String,
    val filePath: String? = null,
)
