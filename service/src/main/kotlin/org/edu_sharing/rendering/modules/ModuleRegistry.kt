package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.modules.exceptions.ModuleNotRegisteredException
import org.springframework.lang.Nullable
import org.springframework.stereotype.Component

@Component
class ModuleRegistry(@Nullable private val modules: List<RenderModule>) {
    private final val registeredModules: Map<RenderModules, RenderModule> = modules.associateBy { it.module() }

    @Suppress("UNCHECKED_CAST")
    fun <T: RenderModule> getRenderModule(module: RenderModules) : T {
        val result = this.registeredModules[module] ?: throw ModuleNotRegisteredException(module)
        return result as T
    }
}
