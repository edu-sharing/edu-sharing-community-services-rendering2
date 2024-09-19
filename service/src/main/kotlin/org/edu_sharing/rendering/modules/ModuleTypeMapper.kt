package org.edu_sharing.rendering.modules

interface ModuleTypeMapper {
    fun moduleTypeAssociations() : List<Pair<ModuleTypeDefinition, RenderModule>>
}
