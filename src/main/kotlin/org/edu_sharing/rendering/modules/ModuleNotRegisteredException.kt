package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.dto.RenderModules

class ModuleNotRegisteredException(module: RenderModules) : Throwable(module.name) {

}
