package org.edu_sharing.rendering.utils

fun String.combinePath(vararg path: String): String {
    val base = this.trimEnd('/')
    val combinedPath = path.joinToString("/") { it.trimStart('/') }
    return "${base}/${combinedPath}"
}

fun String.cleanUrl(): String {
    if(this.startsWith("http") && (this.contains(":80/") || this.endsWith(":80")) ){
        return this.replace(":80", "")
    }

    if(this.startsWith("https") && (this.contains(":443/") || this.endsWith(":443"))){
        return this.replace(":443", "")
    }

    return this
}
