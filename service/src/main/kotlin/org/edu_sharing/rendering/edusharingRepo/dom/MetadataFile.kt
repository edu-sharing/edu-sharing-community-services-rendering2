package org.edu_sharing.rendering.edusharingRepo.dom

import java.io.File

data class MetadataFile(val file: File): AutoCloseable {
    override fun close() {
        file.delete()
    }
}
