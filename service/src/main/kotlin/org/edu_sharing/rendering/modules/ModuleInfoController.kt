package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@ConditionalOnController
@RestController
@RequestMapping("/info/modules")
class ModuleInfoController(
    private val moduleRegistry: ModuleRegistry
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getModulesInfo(): List<RenderModuleInfo> {
        return moduleRegistry.getModuleTypeMapperList()
            .flatMap { it.moduleTypeAssociations() }
            .map {
                RenderModuleInfo(
                    name = it.second.module(),
                    typeMapping = it.first
                )
            }
    }
}