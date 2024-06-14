package org.edu_sharing.rendering.processing.av

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.util.*

class AvFileHelper(
    private val storageImplementation: StorageService
) {
    lateinit var outputFile: File
    lateinit var originalFile: File

    fun initOutputTempFile(extension: String) {
        outputFile = File("${UUID.randomUUID()}.$extension")
    }


    fun fetchOriginalTempFile(cacheObject: CacheObject) {
        val originalFileName = buildString {
            append(UUID.randomUUID().toString())
            append(".")
            append(cacheObject.mimeType.substringAfter('/'))
        }
        originalFile = File(originalFileName)
        val fileInputStream = storageImplementation.getObjectStream(cacheObject, true)
        fileInputStream.use {
            Files.copy(fileInputStream, originalFile.toPath())
        }
    }

    fun uploadToCache(cacheObject: CacheObject, metaData: Map<String, String> = emptyMap()) {
        if (!::outputFile.isInitialized) {
            throw Exception("Output file not initialized")
        }
        storageImplementation.putObject(cacheObject, outputFile.readBytes().inputStream(), metaData)
    }

    fun cleanup() {
        if(::outputFile.isInitialized) {outputFile.delete()}
        if(::originalFile.isInitialized) {originalFile.delete()}
    }
}