package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.storage.StaticStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


@ConditionalOnConverter
@Service
class EduHtmlConversionService(
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StaticStorageService
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    fun cacheData(cacheObject: CacheObject) {
        log.debug("Downloading EduHTML content for nodeId ${cacheObject.nodeId}, hash ${cacheObject.hash}")
        val tempZip = Files.createTempFile("eduhtml-", ".zip")

        try {
            contentTransferService.getAsInputStream(cacheObject).use { input ->
                Files.copy(input, tempZip, StandardCopyOption.REPLACE_EXISTING)
            }

            val zipRoot = Files.newInputStream(tempZip).use { firstPass ->
                getZipRootPath(ZipInputStream(firstPass))
            }
            log.debug("EduHTML zip root resolved to '$zipRoot' for nodeId ${cacheObject.nodeId}, extracting to storage")

            Files.newInputStream(tempZip).use { secondPass ->
                unzipArchive(cacheObject, ZipInputStream(secondPass), zipRoot)
            }
            log.debug("EduHTML extraction complete for nodeId ${cacheObject.nodeId}")
        } finally {
            try {
                Files.deleteIfExists(tempZip)
            } catch (e: Exception) {
                log.warn("Could not delete temporary zip file: ${tempZip.toAbsolutePath()}", e)
            }
        }
    }

    private fun unzipArchive(cacheObject: CacheObject, zipInputStream: ZipInputStream, zipRoot: String) {
        var currentEntry: ZipEntry? = zipInputStream.nextEntry
        while (currentEntry != null) {
            if (!currentEntry.isDirectory || !currentEntry.name.startsWith(zipRoot)) {
                val extractedCacheObject = cacheObject
                    .copy(mimeType = URLConnection.guessContentTypeFromName(currentEntry.name))
                extractedCacheObject.size = -1

                storageImplementation.putObject(
                    extractedCacheObject,
                    zipInputStream,
                    currentEntry.name.substringAfter(zipRoot)
                )
            }
            currentEntry = zipInputStream.nextEntry
        }
    }

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
