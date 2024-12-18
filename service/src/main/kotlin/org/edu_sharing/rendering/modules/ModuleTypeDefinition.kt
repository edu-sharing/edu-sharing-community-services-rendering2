package org.edu_sharing.rendering.modules

data class ModuleTypeDefinition(
    val type: String? = null,
    val mimeTypePrefix: String? = null,
    val mimeTypeSuffix: String? = null,
    val replicationSource: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ModuleTypeDefinition

        if (type != other.type) return false
        if (mimeTypePrefix != other.mimeTypePrefix) return false
        if (mimeTypeSuffix != other.mimeTypeSuffix) return false
        if (replicationSource != other.replicationSource) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type?.hashCode() ?: 0
        result = 31 * result + (mimeTypePrefix?.hashCode() ?: 0)
        result = 31 * result + (mimeTypeSuffix?.hashCode() ?: 0)
        result = 31 * result + (replicationSource?.hashCode() ?: 0)
        return result
    }
}
