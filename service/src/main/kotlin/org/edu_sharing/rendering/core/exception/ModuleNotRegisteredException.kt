package org.edu_sharing.rendering.core.exception

import org.edu_sharing.rendering.modules.RenderModules

class ModuleNotRegisteredException(module: RenderModules) : Exception(module.name)
