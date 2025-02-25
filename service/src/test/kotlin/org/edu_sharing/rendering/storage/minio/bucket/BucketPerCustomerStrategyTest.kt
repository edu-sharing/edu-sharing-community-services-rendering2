package org.edu_sharing.rendering.storage.minio.bucket

import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class BucketPerCustomerStrategyTest {
    private val underTest = BucketPerCustomerStrategy()

    /*@Test
    fun testGetCacheObjectRootPathReturnsTypeNodeHash() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "node123",
            type = "file-pdf",
            hash = "abc123",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = null
        )

        // Act
        val result = underTest.getCacheObjectRootPath(cacheObject)

        // Assert
        assert(result == "file-pdf/node123/abc123")
    }

    @Test
    fun testGetBucketReturnsNodeId() {
        // Arrange
        val cacheObject = mockk<CacheObject>()

        every {cacheObject.repoId} returns "repo123123"

        // Act
        val result = underTest.getBucket(cacheObject)

        // Assert
        assert(result == "repo123123")
    }

    @Test
    fun testPrefixStaticPathPrefixesRepoIdBeforeStoragePath() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "node123",
            type = "file-pdf",
            hash = "abc123",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = null
        )
        val path = "somepath/123"

        // Act
        val result = underTest.prefixStaticPath(cacheObject, path)

        val expected = "/repoId123${underTest.getStoragePath(cacheObject, path)}"
        // Assert
        assert(result == expected)
    }*/
}