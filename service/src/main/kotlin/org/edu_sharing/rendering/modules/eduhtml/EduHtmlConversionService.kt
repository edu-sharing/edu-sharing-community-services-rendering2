package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.storage.StaticStorageService
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
        val inputStream = contentTransferService.getAsInputStream(cacheObject)
        inputStream.mark(0)
        inputStream.use {
            val zipRoot = getZipRootPath(ZipInputStream(it))
            inputStream.reset()
            unzipArchive(cacheObject, ZipInputStream(it), zipRoot)
        }
    }

    /**
     * Function unzipArchive
     *
     * Unzips all files and folders below the provided root path to blob storage
     */
    private fun unzipArchive(cacheObject: CacheObject, zipInputStream: ZipInputStream, zipRoot: String) {
        var currentEntry: ZipEntry? = zipInputStream.nextEntry
        while (currentEntry != null) {
            if (!currentEntry.isDirectory || !currentEntry.name.startsWith(zipRoot)) {
                val extractedCacheObject = cacheObject
                    .copy(mimeType = URLConnection.guessContentTypeFromName(currentEntry.name))
                extractedCacheObject.size = -1

                storageImplementation.putObject(extractedCacheObject, zipInputStream, currentEntry.name.substringAfter(zipRoot))
            }
            currentEntry = zipInputStream.nextEntry
        }
    }

    /**
     * Function getZipRootPath
     *
     * Discerns the zip root path (i.e. the folder containing the index file)
     */
    private fun getZipRootPath(zipInputStream: ZipInputStream): String {
        var currentEntry: ZipEntry? = zipInputStream.nextEntry
        while (currentEntry != null) {
            if (!currentEntry.isDirectory) {
                val splitPath = currentEntry.name.split("/").toMutableList()
                val fileName = splitPath.last()
                if (fileName == "index.html" || fileName == "index.htm") {
                    splitPath.removeLast()
                    return splitPath.toList().joinToString("/")
                }
            }
            currentEntry = zipInputStream.nextEntry
        }
        throw ConversionException("Archive does not contain index.html or index.htm file or does not contain any files")
    }
}
