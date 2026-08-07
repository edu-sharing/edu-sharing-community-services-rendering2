package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.storage.StaticStorageService
import org.edu_sharing.rendering.utils.MimeTypeResolver
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
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

            try {
                ZipFile(tempZip.toFile()).use { zipFile ->
                    val zipRoot = getZipRootPath(zipFile, candidates)
                    log.debug("EduHTML zip root resolved to '$zipRoot' for nodeId ${cacheObject.nodeId}, extracting to storage")
                    unzipArchive(cacheObject, zipFile, zipRoot)
                }
                markExtractionComplete(cacheObject)
            } catch (exception: Exception) {
                // Never leave a half-extracted archive behind. The completion marker already keeps it
                // from being served, but the orphaned objects would still occupy storage and leave the
                // tracking entry with a stale size until the next successful render.
                purgePartialExtraction(cacheObject)
                throw exception
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
            // Directory entries carry no content, so only files are stored.
            if (entry.isDirectory) {
                continue
            }
            // Files outside [zipRoot] keep their full path - removePrefix is a no-op for them.
            val targetPath = sanitizeTargetPath(entry.name.removePrefix(zipRoot))
            if (targetPath == null) {
                log.warn("Skipping EduHTML archive entry '${entry.name}' for nodeId ${cacheObject.nodeId}: unsafe path")
                continue
            }

            val extractedCacheObject = cacheObject
                .copy(mimeType = MimeTypeResolver.fromFileName(entry.name))
            extractedCacheObject.size = entry.size

            zipFile.getInputStream(entry).use { entryStream ->
                storageImplementation.putObject(extractedCacheObject, entryStream, targetPath)
            }
        }
    }

    /**
     * Rejects archive-relative paths that would place an object outside the cache object's storage
     * prefix. Entry names end up in the storage key verbatim
     * (`BaseBucketStrategy.getStoragePath(cacheObject, path)`), and that prefix is what quota
     * tracking, the cache cleaner and the static asset lookup are scoped to.
     */
    private fun sanitizeTargetPath(rawPath: String): String? {
        val normalized = rawPath.trimStart('/')
        if (normalized.isBlank() || normalized.split('/').any { it == ".." }) {
            return null
        }
        return normalized
    }

    private fun markExtractionComplete(cacheObject: CacheObject) {
        val markerCacheObject = cacheObject.copy(mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
        markerCacheObject.size = 0
        storageImplementation.putObject(
            markerCacheObject,
            ByteArrayInputStream(ByteArray(0)),
            EduHtmlService.COMPLETION_MARKER_PATH
        )
    }

    private fun purgePartialExtraction(cacheObject: CacheObject) {
        try {
            storageImplementation.removeObjects(listOf(cacheObject), isTemp = false)
        } catch (exception: Exception) {
            log.warn("Could not purge partial EduHTML extraction for nodeId ${cacheObject.nodeId}", exception)
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
