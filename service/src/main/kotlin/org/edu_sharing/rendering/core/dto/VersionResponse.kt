package org.edu_sharing.rendering.core.dto

/**
 * Build info of this service, read by the repository for its status report (cve tracking) and shown in the admin frontend.
 * Unknown values (e.g. a local build without git info) are null. [buildTime] is the ISO-8601 instant of the build.
 */
data class VersionResponse(
    val version: String?,
    val commit: String?,
    val imageTag: String?,
    val buildTime: String? = null,
)
