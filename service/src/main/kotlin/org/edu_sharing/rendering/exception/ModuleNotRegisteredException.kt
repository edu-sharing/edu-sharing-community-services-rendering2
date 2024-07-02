package org.edu_sharing.rendering.exception

import org.edu_sharing.rendering.dto.RenderModules

class ModuleNotRegisteredException(module: RenderModules) : Exception(module.name)
