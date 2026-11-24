package org.edu_sharing.rendering.core

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.core.dto.VersionResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Version of this service for the admin frontend. Not scoped to a repoId: it describes the service itself.
 */
@RestController
@RequestMapping("/admin")
@SecurityRequirement(name = "basicAuth")
@Tag(name = "system")
@ConditionalOnMaster
class AdminVersionController(
    private val buildInfoService: BuildInfoService,
) {

    @GetMapping("/version")
    fun getVersion(): VersionResponse = buildInfoService.getVersion()
}
