package org.edu_sharing.rendering.asset.dto

import java.io.InputStream

data class ReadableAsset(
    val mimeType: String,
    val fileSize: Long,
    val range: String = "",
    val stream: InputStream
)
