package org.edu_sharing.rendering.utils

fun String.combinePath(vararg path: String): String {
    val base = this.trimEnd('/')
    val combinedPath = path.joinToString("/") { it.trimStart('/') }
    return "${base}/${combinedPath}"
}
