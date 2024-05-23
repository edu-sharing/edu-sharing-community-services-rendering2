package org.edu_sharing.rendering.processing.eduhtml

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.exception.ConversionException
import org.edu_sharing.rendering.service.ContentTransferService
import org.springframework.stereotype.Service
import java.net.URLConnection
import java.util.zip.ZipEntry
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

    private fun unzipArchive(cacheObject: CacheObject, zipInputStream: ZipInputStream) {
        var archiveIsEmpty = true
        var zipRootPath = ""
        var currentEntry: ZipEntry? = zipInputStream.nextEntry
        while (currentEntry != null) {
            if (zipRootPath.isBlank()) zipRootPath = currentEntry.name
            if (!currentEntry.isDirectory) {
                archiveIsEmpty = false
                val extractedCacheObject = cacheObject
                    .copy(mimeType = URLConnection.guessContentTypeFromName(currentEntry.name))
                val objectName = "${cacheObject.nodeId}/${currentEntry.name.substringAfter(zipRootPath)}"
                storageImplementation.putObject(extractedCacheObject, zipInputStream, objectName)
            }
            currentEntry = zipInputStream.nextEntry
        }
        if (archiveIsEmpty) throw ConversionException("Archive does not contain any files")
    }
}
