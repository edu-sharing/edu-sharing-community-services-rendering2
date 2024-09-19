package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.rendering.storage.StaticStorageService
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.springframework.stereotype.Service
import java.net.URLConnection
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

@ConditionalOnConverter
@Service
class EduHtmlConversionService(
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StaticStorageService
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

                storageImplementation.putObject(extractedCacheObject, zipInputStream, currentEntry.name.substringAfter(zipRootPath))
            }
            currentEntry = zipInputStream.nextEntry
        }
        if (archiveIsEmpty) throw ConversionException("Archive does not contain any files")
    }
}
