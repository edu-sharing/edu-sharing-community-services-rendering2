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
import java.util.zip.ZipFile


@ConditionalOnConverter
@Service
class EduHtmlConversionService(
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StaticStorageService
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    fun cacheData(cacheObject: CacheObject, candidates: List<String>) {
        log.debug("Downloading EduHTML content for nodeId ${cacheObject.nodeId}, hash ${cacheObject.hash}")
        val tempZip = Files.createTempFile("eduhtml-", ".zip")

        try {
            contentTransferService.getAsInputStream(cacheObject).use { input ->
                Files.copy(input, tempZip, StandardCopyOption.REPLACE_EXISTING)
            }

            ZipFile(tempZip.toFile()).use { zipFile ->
                val zipRoot = getZipRootPath(zipFile, candidates)
                log.debug("EduHTML zip root resolved to '$zipRoot' for nodeId ${cacheObject.nodeId}, extracting to storage")
                unzipArchive(cacheObject, zipFile, zipRoot)
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

    private fun unzipArchive(cacheObject: CacheObject, zipFile: ZipFile, zipRoot: String) {
        for (entry in zipFile.entries()) {
            if (!entry.isDirectory || !entry.name.startsWith(zipRoot)) {
                val extractedCacheObject = cacheObject
                    .copy(mimeType = URLConnection.guessContentTypeFromName(entry.name))
                extractedCacheObject.size = entry.size

                zipFile.getInputStream(entry).use { entryStream ->
                    storageImplementation.putObject(
                        extractedCacheObject,
                        entryStream,
                        entry.name.substringAfter(zipRoot)
                    )
                }
            }
        }
    }

    /**
     * Locates the archive root by finding the entry-point file. Entries are scanned once and
     * matched against [candidates] (an entry matches a candidate when its path equals the
     * candidate or ends with `"/" + candidate`, i.e. on a path-segment boundary). The root for
     * the **highest-priority** matched candidate is returned, so e.g. `index.html` wins over a
     * fallback `story.html` when both are present.
     */
    private fun getZipRootPath(zipFile: ZipFile, candidates: List<String>): String {
        val rootsByCandidate = HashMap<String, String>()
        for (entry in zipFile.entries()) {
            if (!entry.isDirectory) {
                val name = entry.name
                for (candidate in candidates) {
                    if (candidate !in rootsByCandidate &&
                        (name == candidate || name.endsWith("/$candidate"))
                    ) {
                        rootsByCandidate[candidate] = name.removeSuffix(candidate)
                    }
                }
            }
        }
        candidates.firstOrNull { it in rootsByCandidate }?.let { return rootsByCandidate.getValue(it) }
        throw ConversionException(
            "Archive does not contain any of the expected entry-point files $candidates or does not contain any files"
        )
    }
}
