package org.edu_sharing.rendering.core

import org.edu_sharing.rendering.core.dto.VersionResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component

/**
 * Build info of this service from `META-INF/build-info.properties` (`spring-boot-maven-plugin` goal `build-info`), shown
 * to a registered repository ([VersionController]) and in the admin frontend ([AdminVersionController]).
 */
@Component
class BuildInfoService(
    private val buildProperties: ObjectProvider<BuildProperties>,
) {

    fun getVersion(): VersionResponse {
        val build = buildProperties.ifAvailable
        return VersionResponse(
            version = filtered(build?.version),
            commit = filtered(build?.get("git.commit")),
            imageTag = filtered(build?.get("docker.tag")),
            buildTime = build?.time?.toString(),
        )
    }

    /**
     * maven leaves unknown properties unresolved (e.g. "${git.commit.id}" if git info is missing)
     */
    private fun filtered(value: String?): String? =
        value?.takeIf { it.isNotBlank() && !it.contains("\${") }
}
