package org.edu_sharing.rendering.storage.minio.bucket

import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.core.dto.CacheObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.MediaType

@ExtendWith(MockKExtension::class)
class BucketPerMediaTypeStrategyTest {
    private val underTest = BucketPerMediaTypeStrategy()

    @Test
    fun testGetStoragePathReturnsProperStoragePathForObjectWithoutQuality() {
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
        val result = underTest.getStoragePath(cacheObject)

        assert(result == "node123/abc123.pdf")
    }

    @Test
    fun testGetStoragePathReturnsProperStoragePathForObjectWithQuality() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "node123",
            type = "file-pdf",
            hash = "abc123",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = 5
        )

        // Act
        val result = underTest.getStoragePath(cacheObject)

        assert(result == "node123/abc123_5.pdf")
    }

    @Test
    fun testGetCacheObjectRootPathReturnsExpectedValue() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "node12345",
            type = "file-pdf",
            hash = "abc123abc",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = 5
        )

        //Act
        val result = underTest.getCacheObjectRootPath(cacheObject)

        //Assert
        assert(result == "node12345/abc123abc")
    }

    @Test
    fun testGetBucketReturnsType() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "node12345",
            type = "file-pdf",
            hash = "abc123abc",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = 5
        )

        // Act
        val result = underTest.getBucket(cacheObject)

        // Assert
        assert(result == "file-pdf")
    }

    @Test
    fun testGetStoragePathStaticReturnsExpectedValue() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "node12345",
            type = "file-pdf",
            hash = "abc123abc",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = 5
        )
        val path = "/somepath/path"

        // Act
        val result = underTest.getStoragePath(cacheObject, path)

        // Assert
        assert(result == "/node12345/abc123abc/somepath/path")
    }

    @Test
    fun testGetExtensionFromMimeTypeReturnsValueFromUtilityClass() {
        assert(underTest.getExtensionFromMimeType(MediaType.APPLICATION_PDF_VALUE) == ".pdf")
    }

    @Test
    fun testGetExtensionFromMimeTypeReturnsEmptyStringForEmptyMimeType() {
        assert(underTest.getExtensionFromMimeType("").isBlank())
    }

    @Test
    fun testGetCacheObjectFromStaticPathProperlyDestructuresPath() {
        // Arrange
        val path = "/repo/type/node/hash/somepath/path"

        // Act
        val (cacheObject, location) = underTest.getCacheObjectFromStaticPath(path)

        assert(cacheObject.repoId == "repo")
        assert(cacheObject.type == "type")
        assert(cacheObject.nodeId == "node")
        assert(cacheObject.hash == "hash")
        assert(location == "/somepath/path")
    }

    @Test
    fun testGetCacheObjectFromStaticPathThrowsIllegalArgumentExceptionOnTooFewPathVars() {
        // Arrange
        val path = "/repo/type/node/test"

        // Act and Assert
        assertThrows<IllegalArgumentException> { underTest.getCacheObjectFromStaticPath(path) }
    }

    @Test
    fun assertPrefixStaticPathPrefixesRepoIdAndTypeBeforeStoragePath() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "node12345",
            type = "file-pdf",
            hash = "abc123abc",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = 5
        )
        val path = "somepath"

        // Act
        val result = underTest.prefixStaticPath(cacheObject, path)

        // Assert
        val expected = "/repoId123/file-pdf${underTest.getStoragePath(cacheObject, path)}"
        assert(result == expected)
    }
}