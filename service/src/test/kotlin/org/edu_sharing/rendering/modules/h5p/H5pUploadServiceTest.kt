package org.edu_sharing.rendering.modules.h5p

import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.modules.h5p.lumi.LumiContentManagementService
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Duration
import java.util.UUID

class H5pUploadServiceTest {

    private val contentTransferService = mockk<ContentTransferService>()
    private val lumiContentManagementService = mockk<LumiContentManagementService>()
    private val trackingService = mockk<TrackingService>()
    private val storageService = mockk<StorageService>()
    private val module = mockk<H5pRenderModule>()

    private lateinit var mockServer: MockWebServer
    private lateinit var underTest: H5pUploadService

    /** nodeId deliberately carries a dot — the temp-file prefix is built from `substringBefore(".")`. */
    private val cacheObject = CacheObject(
        nodeId = "node.1",
        type = "h5p",
        hash = "testHash",
        repoId = "repo123"
    )

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        underTest = H5pUploadService(
            contentTransferService = contentTransferService,
            lumiWebClient = WebClient.builder().baseUrl(mockServer.url("/").toString()).build(),
            lumiContentManagementService = lumiContentManagementService,
            trackingService = trackingService,
            storageService = storageService,
            module = module,
            objectMapper = JsonMapper.builder().build()
        )
        clearAllMocks()
        every { module.getCredentials(cacheObject.repoId) } returns emptyMap()
        every { lumiContentManagementService.getContentBucket(cacheObject.repoId) } returns BUCKET
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
        clearAllMocks()
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────────

    /** An input stream that records whether the service closed it (the `use {}` in uploadPackage). */
    private class TrackingInputStream(content: String) : ByteArrayInputStream(content.toByteArray()) {
        var closed = false
        override fun close() {
            closed = true
            super.close()
        }
    }

    private fun enqueueContentId(contentId: String) = mockServer.enqueue(
        MockResponse()
            .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody("""{"contentId": "$contentId"}""")
            .setResponseCode(200)
    )

    private fun enqueueStatus(code: Int) = mockServer.enqueue(MockResponse().setResponseCode(code))

    private fun stubLookup(contentId: String?) {
        every {
            lumiContentManagementService.getContentId(cacheObject.nodeId, cacheObject.hash, any())
        } returns contentId
    }

    /**
     * Spool files matching the prefix the service builds for this node revision. The temp dir is shared, so
     * the assertions below compare a before/after snapshot rather than expecting it to be empty — leftovers
     * from an unrelated run must not fail this test.
     */
    private fun spoolFiles(): Set<String> =
        File(System.getProperty("java.io.tmpdir"))
            .list { _, name -> name.startsWith("${cacheObject.nodeId.substringBefore(".")}_${cacheObject.hash}_") }
            ?.toSet()
            ?: emptySet()

    // ── lookup hit: no upload at all ──────────────────────────────────────────────────────────────

    @Test
    fun testGetContentIdReturnsTheExistingContentIdWithoutUploadingIfLumiAlreadyHoldsIt() {
        // Arrange
        stubLookup("existingContentId")
        justRun { trackingService.trackCacheObject(cacheObject, BUCKET) }

        // Act
        val result = underTest.getContentId(cacheObject)

        // Assert
        assertEquals("existingContentId", result)
        // nothing was uploaded and the source content was never fetched from the repository
        assertEquals(0, mockServer.requestCount)
        verify(exactly = 1) { trackingService.trackCacheObject(cacheObject, BUCKET, null) }
        confirmVerified(trackingService, contentTransferService, storageService)
    }

    @Test
    fun testGetContentIdTracksWithoutASizeOnALookupHit() {
        // The object is already in the bucket, so only its lastAccessed is refreshed — re-measuring the
        // directory would be a pointless S3 walk. `size` is the observable difference: the 2- and 3-arg
        // call forms are the same JVM method (default parameter), so the hit path is pinned by size == null.
        // Arrange
        stubLookup("existingContentId")
        val sizes = mutableListOf<Long?>()
        every { trackingService.trackCacheObject(cacheObject, BUCKET, captureNullable(sizes)) } returns Unit

        // Act
        underTest.getContentId(cacheObject)

        // Assert
        assertEquals(listOf<Long?>(null), sizes)
        verify(exactly = 0) { storageService.getDirectorySize(any(), any()) }
    }

    // ── lookup miss: upload ───────────────────────────────────────────────────────────────────────

    @Test
    fun testGetContentIdUploadsThePackageAndReturnsTheNewContentIdOnALookupMiss() {
        // Arrange
        val fileContent = UUID.randomUUID().toString()
        stubLookup(null)
        every { contentTransferService.getAsInputStream(cacheObject) } returns TrackingInputStream(fileContent)
        every { storageService.getDirectorySize(BUCKET, "newContentId") } returns 4711L
        justRun { trackingService.trackCacheObject(cacheObject, BUCKET, 4711L) }
        enqueueContentId("newContentId")

        // Act
        val result = underTest.getContentId(cacheObject)

        // Assert
        assertEquals("newContentId", result)

        val request = mockServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/edusharing", request.path)
        assertTrue(
            request.getHeader("Content-Type")!!.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE),
            "expected a multipart upload, got ${request.getHeader("Content-Type")}"
        )

        val body = request.body.readUtf8()
        // the file part is named after the node revision, with the nodeId truncated at the first dot
        assertTrue(body.contains("""name="file"; filename="node_testHash_"""), body)
        assertTrue(body.contains(fileContent), "uploaded body does not carry the source content")
        // lumi keys its own mapping on nodeId_hash — this is what the lookup later resolves against
        assertTrue(body.contains("""name="nodeId""""), body)
        assertTrue(body.contains("${cacheObject.nodeId}_${cacheObject.hash}"), body)

        // a fresh import is measured and tracked WITH its size (the hit path passes null instead)
        verify(exactly = 1) { storageService.getDirectorySize(BUCKET, "newContentId") }
        verify(exactly = 1) { trackingService.trackCacheObject(cacheObject, BUCKET, 4711L) }
        verify(exactly = 0) { trackingService.trackCacheObject(cacheObject, BUCKET, null) }
    }

    @Test
    fun testUploadClosesTheSourceStreamAndDeletesTheTempFile() {
        // Arrange
        val sourceStream = TrackingInputStream("payload")
        stubLookup(null)
        every { contentTransferService.getAsInputStream(cacheObject) } returns sourceStream
        every { storageService.getDirectorySize(BUCKET, "newContentId") } returns 1L
        justRun { trackingService.trackCacheObject(cacheObject, BUCKET, 1L) }
        enqueueContentId("newContentId")
        val spoolFilesBefore = spoolFiles()

        // Act
        underTest.getContentId(cacheObject)

        // Assert
        assertTrue(sourceStream.closed, "the source stream from the repository was not closed")
        assertEquals(
            emptySet<String>(),
            spoolFiles() - spoolFilesBefore,
            "the spooled .h5p temp file was not deleted"
        )
    }

    // ── failures ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun testGetContentIdPropagatesALookupFailureWithoutUploading() {
        // A lumi outage must not be mistaken for "not imported yet" — uploading again would duplicate
        // content that may well exist.
        // Arrange
        every {
            lumiContentManagementService.getContentId(cacheObject.nodeId, cacheObject.hash, any())
        } throws IllegalStateException("lumi unreachable")

        // Act and assert
        assertThrows<IllegalStateException> { underTest.getContentId(cacheObject) }
        assertEquals(0, mockServer.requestCount)
        verify(exactly = 0) { contentTransferService.getAsInputStream(any()) }
        verify(exactly = 0) { trackingService.trackCacheObject(any(), any(), any()) }
    }

    @Test
    fun testGetContentIdPropagatesAnUploadFailureAndTracksNothing() {
        // Arrange
        val sourceStream = TrackingInputStream("payload")
        stubLookup(null)
        every { contentTransferService.getAsInputStream(cacheObject) } returns sourceStream
        enqueueStatus(500)
        val spoolFilesBefore = spoolFiles()

        // Act and assert
        assertThrows<Exception> { underTest.getContentId(cacheObject) }

        // a failed import must not be tracked as cached content, and must not leak the temp file
        verify(exactly = 0) { trackingService.trackCacheObject(any(), any(), any()) }
        verify(exactly = 0) { storageService.getDirectorySize(any(), any()) }
        assertTrue(sourceStream.closed, "the source stream from the repository was not closed")
        assertEquals(
            emptySet<String>(),
            spoolFiles() - spoolFilesBefore,
            "the spooled .h5p temp file was not deleted"
        )
    }

    // ── timeout ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun testLookupUsesTheRepositoryConfiguredTimeout() {
        // Arrange
        every { module.getCredentials(cacheObject.repoId) } returns mapOf("timeout" to "42")
        val timeoutSlot = slot<Duration>()
        every {
            lumiContentManagementService.getContentId(cacheObject.nodeId, cacheObject.hash, capture(timeoutSlot))
        } returns "existingContentId"
        justRun { trackingService.trackCacheObject(cacheObject, BUCKET) }

        // Act
        underTest.getContentId(cacheObject)

        // Assert
        assertEquals(Duration.ofSeconds(42), timeoutSlot.captured)
    }

    @Test
    fun testLookupFallsBackToTheDefaultTimeoutIfTheRepositoryConfiguresNoneOrAnInvalidOne() {
        // Arrange
        every { module.getCredentials(cacheObject.repoId) } returns mapOf("timeout" to "not-a-number")
        val timeoutSlot = slot<Duration>()
        every {
            lumiContentManagementService.getContentId(cacheObject.nodeId, cacheObject.hash, capture(timeoutSlot))
        } returns "existingContentId"
        justRun { trackingService.trackCacheObject(cacheObject, BUCKET) }

        // Act
        underTest.getContentId(cacheObject)

        // Assert
        assertEquals(DEFAULT_TIMEOUT, timeoutSlot.captured)
    }

    companion object {
        private const val BUCKET = "contentBucket123"
        private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(300)
    }
}
