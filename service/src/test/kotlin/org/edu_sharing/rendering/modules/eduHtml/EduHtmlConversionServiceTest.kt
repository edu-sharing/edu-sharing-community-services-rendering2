package org.edu_sharing.rendering.modules.eduHtml

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlConversionService
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlService
import org.edu_sharing.rendering.storage.StaticStorageService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@ExtendWith(MockKExtension::class)
class EduHtmlConversionServiceTest {
    private val contentTransferService: ContentTransferService = mockk()
    private val storageService: StaticStorageService = mockk()
    private val underTest = EduHtmlConversionService(contentTransferService, storageService)

    private val defaultCandidates = listOf("index.html", "index.htm", "story.html")

    /**
     * Builds an in-memory zip. Entry names ending in `/` become directory entries (which is what a
     * real archive contains and what `ZipFile.entries()` reports as `isDirectory`).
     */
    private fun zipOf(vararg entries: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for (entry in entries) {
                zip.putNextEntry(ZipEntry(entry))
                if (!entry.endsWith("/")) {
                    zip.write("content of $entry".toByteArray())
                }
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Runs cacheData over an in-memory zip and returns the relative target paths that were stored. */
    private fun storedPathsFor(zip: ByteArray, candidates: List<String>): List<String> =
        storedObjectsFor(zip, candidates).map { it.first }

    /**
     * Runs cacheData over an in-memory zip and returns the stored (relative target path, mime type)
     * pairs, excluding the completion marker.
     */
    private fun storedObjectsFor(zip: ByteArray, candidates: List<String>): List<Pair<String, String>> {
        val cacheObject = CacheObject(nodeId = "nodeId", hash = "hash", type = "eduhtml", repoId = "repo123")
        every { contentTransferService.getAsInputStream(cacheObject) } returns ByteArrayInputStream(zip)
        val paths = mutableListOf<String>()
        val objects = mutableListOf<CacheObject>()
        justRun {
            storageService.putObject(
                cacheObject = capture(objects),
                inputStream = any(),
                targetPath = capture(paths),
                metadata = any()
            )
        }
        justRun { storageService.removeObjects(any(), any()) }
        underTest.cacheData(cacheObject, candidates)
        return paths.zip(objects.map { it.mimeType })
            .filterNot { it.first == EduHtmlService.COMPLETION_MARKER_PATH }
    }

    @Test
    fun picksIndexHtmlAtRootLevel() {
        val paths = storedPathsFor(zipOf("index.html", "style.css", "assets/app.js"), defaultCandidates)
        assert(paths.toSet() == setOf("index.html", "style.css", "assets/app.js")) { paths.toString() }
    }

    @Test
    fun stripsWrappingFolderUsingIndexHtml() {
        val paths = storedPathsFor(zipOf("wrap/index.html", "wrap/style.css"), defaultCandidates)
        assert(paths.toSet() == setOf("index.html", "style.css")) { paths.toString() }
    }

    @Test
    fun fallsBackToStoryHtmlWhenNoIndex() {
        val paths = storedPathsFor(zipOf("pkg/story.html", "pkg/app.js"), defaultCandidates)
        assert(paths.toSet() == setOf("story.html", "app.js")) { paths.toString() }
    }

    @Test
    fun prefersIndexHtmlOverStoryHtmlWhenBothPresent() {
        // index.html and story.html live under different roots; index.html wins so its root ("a/") is stripped.
        val paths = storedPathsFor(zipOf("a/index.html", "a/app.js", "b/story.html"), defaultCandidates)
        assert(paths.contains("index.html")) { paths.toString() }
        assert(paths.contains("app.js")) { paths.toString() }
        // story.html sits outside the chosen root, so its full path is retained.
        assert(paths.contains("b/story.html")) { paths.toString() }
    }

    @Test
    fun honorsCustomMainEntityCandidate() {
        val paths = storedPathsFor(zipOf("pkg/content/start.html", "pkg/content/a.js"), listOf("content/start.html"))
        assert(paths.toSet() == setOf("content/start.html", "content/a.js")) { paths.toString() }
    }

    @Test
    fun throwsWhenNoCandidateMatches() {
        val cacheObject = CacheObject(nodeId = "nodeId", hash = "hash", type = "eduhtml", repoId = "repo123")
        every { contentTransferService.getAsInputStream(cacheObject) } returns ByteArrayInputStream(zipOf("readme.txt"))
        justRun { storageService.removeObjects(any(), any()) }
        assertThrows<ConversionException> {
            underTest.cacheData(cacheObject, defaultCandidates)
        }
    }

    @Test
    fun storesEntriesWithExtensionsUnknownToTheJdkMimeTable() {
        // Regression: URLConnection.guessContentTypeFromName returns null for these, and the
        // non-null CacheObject.mimeType turned that into an NPE that aborted the whole extraction
        // mid-archive - which is what broke embedded H5P (its libraries ship web fonts).
        val stored = storedObjectsFor(
            zipOf("index.html", "fonts/icons.woff2", "fonts/icons.ttf", "pkg.h5p", "app.js.map", "LICENSE"),
            defaultCandidates
        ).toMap()

        assert(stored.keys == setOf("index.html", "fonts/icons.woff2", "fonts/icons.ttf", "pkg.h5p", "app.js.map", "LICENSE")) {
            stored.toString()
        }
        assert(stored["fonts/icons.woff2"] == "font/woff2") { stored.toString() }
        assert(stored["fonts/icons.ttf"] == "font/ttf") { stored.toString() }
        assert(stored["pkg.h5p"] == "application/octet-stream") { stored.toString() }
        assert(stored["app.js.map"] == "application/octet-stream") { stored.toString() }
        assert(stored["LICENSE"] == "text/plain") { stored.toString() }
    }

    @Test
    fun storesWebAssetsWithTypesTheBrowserAcceptsUnderNosniff() {
        val stored = storedObjectsFor(
            zipOf("index.html", "app.js", "style.css", "h5p.json", "logo.svg"),
            defaultCandidates
        ).toMap()

        assert(stored["index.html"] == "text/html") { stored.toString() }
        assert(stored["app.js"] == "text/javascript") { stored.toString() }
        assert(stored["style.css"] == "text/css") { stored.toString() }
        assert(stored["h5p.json"] == "application/json") { stored.toString() }
        assert(stored["logo.svg"] == "image/svg+xml") { stored.toString() }
    }

    @Test
    fun skipsDirectoryEntriesIncludingThoseOutsideTheWinningRoot() {
        val paths = storedPathsFor(
            zipOf("wrap/", "wrap/index.html", "wrap/sub/", "wrap/sub/a.js", "other/", "other/b.js"),
            defaultCandidates
        )
        // Directories are never stored; files outside the winning root keep their full path.
        assert(paths.toSet() == setOf("index.html", "sub/a.js", "other/b.js")) { paths.toString() }
    }

    @Test
    fun skipsEntriesThatWouldEscapeTheCacheObjectPrefix() {
        val paths = storedPathsFor(zipOf("index.html", "../escape.js", "a/../../escape2.js"), defaultCandidates)
        assert(paths.toSet() == setOf("index.html")) { paths.toString() }
    }

    @Test
    fun writesCompletionMarkerAfterAllEntries() {
        val cacheObject = CacheObject(nodeId = "nodeId", hash = "hash", type = "eduhtml", repoId = "repo123")
        every { contentTransferService.getAsInputStream(cacheObject) } returns
            ByteArrayInputStream(zipOf("index.html", "app.js"))
        val paths = mutableListOf<String>()
        justRun {
            storageService.putObject(
                cacheObject = any(),
                inputStream = any(),
                targetPath = capture(paths),
                metadata = any()
            )
        }

        underTest.cacheData(cacheObject, defaultCandidates)

        assert(paths.last() == EduHtmlService.COMPLETION_MARKER_PATH) { paths.toString() }
        verify(exactly = 0) { storageService.removeObjects(any(), any()) }
    }

    @Test
    fun purgesPartialExtractionWhenAnEntryFailsToUpload() {
        val cacheObject = CacheObject(nodeId = "nodeId", hash = "hash", type = "eduhtml", repoId = "repo123")
        every { contentTransferService.getAsInputStream(cacheObject) } returns
            ByteArrayInputStream(zipOf("index.html", "app.js"))
        every {
            storageService.putObject(cacheObject = any(), inputStream = any(), targetPath = any(), metadata = any())
        } throws IllegalStateException("s3 down")
        justRun { storageService.removeObjects(any(), any()) }

        assertThrows<IllegalStateException> {
            underTest.cacheData(cacheObject, defaultCandidates)
        }

        verify(exactly = 1) { storageService.removeObjects(listOf(cacheObject), false) }
    }
}
