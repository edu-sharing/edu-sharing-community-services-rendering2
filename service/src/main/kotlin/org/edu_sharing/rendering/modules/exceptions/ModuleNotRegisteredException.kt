package org.edu_sharing.rendering.modules.exceptions

import org.edu_sharing.rendering.modules.RenderModules

class ModuleNotRegisteredException(module: RenderModules) : Exception(module.name)
