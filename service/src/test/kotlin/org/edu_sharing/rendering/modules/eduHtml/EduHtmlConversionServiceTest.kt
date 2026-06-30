package org.edu_sharing.rendering.modules.eduHtml

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlConversionService
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

    private fun zipOf(vararg entries: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for (entry in entries) {
                zip.putNextEntry(ZipEntry(entry))
                zip.write("content of $entry".toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Runs cacheData over an in-memory zip and returns the relative target paths that were stored. */
    private fun storedPathsFor(zip: ByteArray, candidates: List<String>): List<String> {
        val cacheObject = CacheObject(nodeId = "nodeId", hash = "hash", type = "eduhtml", repoId = "repo123")
        every { contentTransferService.getAsInputStream(cacheObject) } returns ByteArrayInputStream(zip)
        val paths = mutableListOf<String>()
        justRun {
            storageService.putObject(
                cacheObject = any(),
                inputStream = any(),
                targetPath = capture(paths),
                metadata = any()
            )
        }
        underTest.cacheData(cacheObject, candidates)
        return paths
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
        assertThrows<ConversionException> {
            underTest.cacheData(cacheObject, defaultCandidates)
        }
    }
}
