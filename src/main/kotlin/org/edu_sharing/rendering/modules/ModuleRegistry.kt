package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.exception.ModuleNotRegisteredException
import org.springframework.lang.Nullable
import org.springframework.stereotype.Component

@Component
class ModuleRegistry(@Nullable private val modules: List<RenderModule>) {
    private final val registeredModules: Map<RenderModules, RenderModule> = modules.associateBy { it.module() }

    fun getRenderModule(module: RenderModules) : RenderModule {
        return this.registeredModules[module] ?: throw ModuleNotRegisteredException(module)
    }
}
