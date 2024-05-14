package org.edu_sharing.rendering.processing.eduhtml

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.service.ContentTransferService
import org.springframework.stereotype.Service
import java.net.URLConnection
import java.util.zip.ZipInputStream

@ConditionalOnConverter
@Service
class EduHtmlConversionService(
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StorageService
) {
    fun cacheData(cacheObject: CacheObject) {
        contentTransferService.getAsInputStream(cacheObject).use {
            unzipArchive(cacheObject, ZipInputStream(it))
        }
    }

    fun unzipArchive(cacheObject: CacheObject, zipInputStream: ZipInputStream) {
        var zipEntry = zipInputStream.nextEntry
        val zipRootPath = zipEntry?.name ?: ""
        while (zipEntry != null) {
            if (!zipEntry.isDirectory) {
                val objectName = cacheObject.nodeId + "/" + zipEntry.name.substringAfter(zipRootPath)
                cacheObject.mimeType = URLConnection.guessContentTypeFromName(zipEntry.name)
                storageImplementation.putObject(cacheObject, zipInputStream, objectName)
            }
            zipEntry = zipInputStream.nextEntry
        }
    }
}
