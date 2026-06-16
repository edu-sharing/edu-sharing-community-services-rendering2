package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@ConditionalOnController
@RestController
@RequestMapping("/public/modules")
class ModuleInfoController(
    private val moduleRegistry: ModuleRegistry,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getModulesInfo(@RequestParam repoId: String): List<RenderModuleInfo> {
        log.debug("getModulesInfo requested for repoId='{}'", repoId)
        val repoConfig = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow {IllegalArgumentException("Unknown repository identifier $repoId provided") }
        val result = moduleRegistry.getModuleTypeMapperList()
            .flatMap { it.moduleTypeAssociations() }
            .filter {
                !(it.first.isAllNulls()) && (! it.second.isOptionalModule() || repoConfig.optionalModules.contains(it.second.module()))
            }
            .map {
                RenderModuleInfo(
                    name = it.second.module(),
                    typeMapping = it.first
                )
            }
        log.debug("getModulesInfo for repoId='{}' returning {} module(s)", repoId, result.size)
        return result
    }
}
