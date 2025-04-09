package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.core.dto.CacheObject
import org.springframework.web.reactive.function.client.WebClient
import java.io.InputStream

data class ConverterWebServiceArguments(
    val client: WebClient,
    val originalFileExtension: String,
    val targetMimeType: String,
    val cacheObject: CacheObject,
    val externalServiceMethodPath: String,
    val urlParams: Map<String, String> = emptyMap(),
    val inputStream: InputStream? = null,
)
