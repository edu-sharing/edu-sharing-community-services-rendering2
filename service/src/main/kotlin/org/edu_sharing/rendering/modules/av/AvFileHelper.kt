package org.edu_sharing.rendering.modules.av

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

class AvFileHelper(
    private val storageImplementation: StorageService,
): AutoCloseable {
    private val log = LoggerFactory.getLogger(javaClass)

    lateinit var outputFile: File
    lateinit var originalFile: File

    fun initOutputTempFile(extension: String) {
        outputFile = File.createTempFile(UUID.randomUUID().toString(), ".$extension")
    }

    fun fetchOriginalTempFile(cacheObject: CacheObject) {
        log.debug("Fetching original AV file from storage: nodeId=${cacheObject.nodeId}, mimeType=${cacheObject.mimeType}")
        originalFile = File.createTempFile(UUID.randomUUID().toString(), ".${cacheObject.mimeType.substringAfter('/')}")
        val fileInputStream = storageImplementation.getObjectStream(cacheObject, true)
        fileInputStream.use {
            originalFile.outputStream().use { fileOutputStream -> fileInputStream.copyTo(fileOutputStream) }
        }
        log.debug("Original AV file fetched: size=${originalFile.length()} bytes, nodeId=${cacheObject.nodeId}")
    }

    fun uploadToCache(cacheObject: CacheObject, metaData: Map<String, String> = emptyMap()) {
        if (!::outputFile.isInitialized) {
            throw Exception("Output file not initialized")
        }

        log.debug("Uploading converted AV file to cache: nodeId=${cacheObject.nodeId}, mimeType=${cacheObject.mimeType}, size=${outputFile.length()} bytes")
        storageImplementation.putObject(cacheObject, outputFile.readBytes().inputStream(), metaData)
    }

    override fun close() {
        if(::outputFile.isInitialized) {outputFile.delete()}
        if(::originalFile.isInitialized) {originalFile.delete()}
    }
}
