package org.edu_sharing.rendering.storage.bucket

import io.mockk.every
import io.mockk.mockk
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.entity.ExternalBucket
import org.edu_sharing.rendering.edusharingRepo.entity.ExternalBuckets
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional

class ExternalBucketStrategyTest {
    private val registrations = mockk<RepositoryRegistrationStorageService>()
    private val underTest = ExternalBucketStrategy(registrations)

    private val cacheObject = CacheObject(
        nodeId = "node1", type = "image", hash = "h1", repoId = "repo1", mimeType = "image/png"
    )

    private fun register(rendering: ExternalBucket, temp: ExternalBucket) {
        every { registrations.getRegistrationByRepoId("repo1") } returns Optional.of(
            RepositoryRegistration(
                repoId = "repo1", url = "https://repo1.example.org", publicKey = "key",
                buckets = ExternalBuckets(renderingBucket = rendering, tempBucket = temp)
            )
        )
    }

    @Test
    fun `without a prefix keys stay at the bucket root`() {
        register(ExternalBucket(name = "shared"), ExternalBucket(name = "shared"))

        assertEquals("image/node1/h1", underTest.getCacheObjectRootPath(cacheObject))
        assertEquals("image/node1/h1.png", underTest.getTempStoragePath(cacheObject))
    }

    @Test
    fun `rendering and temp keys live below their own prefix in a shared bucket`() {
        register(
            ExternalBucket(name = "shared", keyPrefix = "/rendering/"),
            ExternalBucket(name = "shared", keyPrefix = "temp")
        )

        assertEquals("rendering/image/node1/h1", underTest.getCacheObjectRootPath(cacheObject))
        assertEquals("rendering/image/node1/h1.png", underTest.getStoragePath(cacheObject))
        assertEquals("rendering/image/node1/h1/a/b.js", underTest.getStoragePath(cacheObject, "/a/b.js"))
        assertEquals("temp/image/node1/h1.png", underTest.getTempStoragePath(cacheObject))
    }

    @Test
    fun `the public static path does not contain the key prefix`() {
        register(ExternalBucket(name = "shared", keyPrefix = "rendering"), ExternalBucket(name = "shared", keyPrefix = "temp"))

        val staticPath = underTest.prefixStaticPath(cacheObject, "a/b.js")

        assertEquals("/repo1/image/node1/h1/a/b.js", staticPath)
        val (parsed, rest) = underTest.getCacheObjectFromStaticPath(staticPath)
        assertEquals("node1", parsed.nodeId)
        assertEquals("/a/b.js", rest)
    }

    @Test
    fun `key prefix is normalized`() {
        assertEquals("", ExternalBucket.normalizeKeyPrefix(null))
        assertEquals("", ExternalBucket.normalizeKeyPrefix(" / "))
        assertEquals("a/b/", ExternalBucket.normalizeKeyPrefix("/a/b//"))
    }
}
