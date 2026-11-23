package org.edu_sharing.rendering.core.dto

/**
 * Build info of this service, read by the repository for its status report (cve tracking).
 * Unknown values (e.g. a local build without git info) are null.
 */
data class VersionResponse(
    val version: String?,
    val commit: String?,
    val imageTag: String?,
)
