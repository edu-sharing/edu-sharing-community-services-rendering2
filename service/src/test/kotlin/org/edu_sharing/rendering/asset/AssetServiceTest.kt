package org.edu_sharing.rendering.asset

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.asset.dto.AssetLinkParams
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.CachedObjectDetails
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.storage.StaticStorageService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.InputStream


@ExtendWith(MockKExtension::class)
class AssetServiceTest {
    private val storageService = mockk<StaticStorageService>()
    private val mapper = mockk<Mapper>()

    private lateinit var underTest: AssetService

    @BeforeEach
    fun setup() {
        underTest = AssetService(storageService, mapper)
        clearAllMocks()
    }

    @Test
    fun testGetAssetReturnsFullStreamIfNoRangeSet() {
        // Arrange
        val assetParams = mockk<AssetLinkParams>()
        val cacheObject = mockk<CacheObject>()
        val fileDetails = CachedObjectDetails(
            size = 3L,
            mimeType = "application/pdf",
        )
        val stream = mockk<InputStream>()

        every { mapper.assetLinkParamsToCacheObject(assetParams) } returns cacheObject
        every { storageService.getFileProperties(cacheObject) } returns fileDetails
        every { storageService.getObjectStream(cacheObject) } returns stream

        // Act
        val result = underTest.getAsset(assetParams, "")

        // Assert
        assert(result.stream == stream)
        assert(result.mimeType == "application/pdf")
        assert(result.fileSize == 3L)

        verifySequence {
            mapper.assetLinkParamsToCacheObject(assetParams)
            storageService.getFileProperties(cacheObject)
            storageService.getObjectStream(cacheObject)
        }
    }

    @Test
    fun testGetAssetReturnsChunkIfValidRangeIsSet() {
        // Arrange
        val assetParams = mockk<AssetLinkParams>()
        val cacheObject = mockk<CacheObject>()
        val fileDetails = CachedObjectDetails(
            size = 4000000,
            mimeType = "video/mp4",
        )
        val stream = mockk<InputStream>()
        val range = "bytes=100000-400000"

        every { mapper.assetLinkParamsToCacheObject(assetParams) } returns cacheObject
        every { storageService.getFileProperties(cacheObject) } returns fileDetails
        every { storageService.getObjectChunkStream(
            cacheObject,
            400000,
            100000,
            false
        )
        } returns stream

        // Act
        val result = underTest.getAsset(assetParams, range)

        // Assert
        assert(result.stream == stream)
        assert(result.mimeType == "video/mp4")
        assert(result.fileSize == 4000000L)
        assert(result.range == "bytes 100000-400000/4000000")

        verifySequence {
            mapper.assetLinkParamsToCacheObject(assetParams)
            storageService.getFileProperties(cacheObject)
            storageService.getObjectChunkStream(
                cacheObject,
                400000,
                100000,
                false
            )
        }
    }

    @Test
    fun testGetAssetReturnsChunkIfValidRangeIsSetByChromeStandards() {
        // Arrange
        val assetParams = mockk<AssetLinkParams>()
        val cacheObject = mockk<CacheObject>()
        val fileDetails = CachedObjectDetails(
            size = 4000000,
            mimeType = "video/mp4",
        )
        val stream = mockk<InputStream>()
        val range = "bytes 100000-400000"

        every { mapper.assetLinkParamsToCacheObject(assetParams) } returns cacheObject
        every { storageService.getFileProperties(cacheObject) } returns fileDetails
        every { storageService.getObjectChunkStream(
            cacheObject,
            400000,
            100000,
            false
        )
        } returns stream

        // Act
        val result = underTest.getAsset(assetParams, range)

        // Assert
        assert(result.stream == stream)
        assert(result.mimeType == "video/mp4")
        assert(result.fileSize == 4000000L)
        assert(result.range == "bytes 100000-400000/4000000")

        verifySequence {
            mapper.assetLinkParamsToCacheObject(assetParams)
            storageService.getFileProperties(cacheObject)
            storageService.getObjectChunkStream(
                cacheObject,
                400000,
                100000,
                false
            )
        }
    }

    @Test
    fun testGetAssetReturnsChunkWithDefaultSizeIfNoRangeEndSet() {
        // Arrange
        val assetParams = mockk<AssetLinkParams>()
        val cacheObject = mockk<CacheObject>()
        val fileDetails = CachedObjectDetails(
            size = 4000000,
            mimeType = "video/mp4",
        )
        val stream = mockk<InputStream>()
        val range = "bytes=100000-"

        every { mapper.assetLinkParamsToCacheObject(assetParams) } returns cacheObject
        every { storageService.getFileProperties(cacheObject) } returns fileDetails
        every { storageService.getObjectChunkStream(
            cacheObject,
            2100000,
            100000,
            false
        )
        } returns stream

        // Act
        val result = underTest.getAsset(assetParams, range)

        // Assert
        assert(result.stream == stream)
        assert(result.mimeType == "video/mp4")
        assert(result.fileSize == 4000000L)
        assert(result.range == "bytes 100000-2100000/4000000")

        verifySequence {
            mapper.assetLinkParamsToCacheObject(assetParams)
            storageService.getFileProperties(cacheObject)
            storageService.getObjectChunkStream(
                cacheObject,
                2100000,
                100000,
                false
            )
        }
    }

    @Test
    fun testGetAssetReturnsAllRemainingBytesIfEndExceedsFileSize() {
        // Arrange
        val assetParams = mockk<AssetLinkParams>()
        val cacheObject = mockk<CacheObject>()
        val fileDetails = CachedObjectDetails(
            size = 400000,
            mimeType = "video/mp4",
        )
        val stream = mockk<InputStream>()
        val range = "bytes=100000-500000"

        every { mapper.assetLinkParamsToCacheObject(assetParams) } returns cacheObject
        every { storageService.getFileProperties(cacheObject) } returns fileDetails
        every { storageService.getObjectChunkStream(
            cacheObject,
            399999,
            100000,
            false
        )
        } returns stream

        // Act
        val result = underTest.getAsset(assetParams, range)

        // Assert
        assert(result.stream == stream)
        assert(result.mimeType == "video/mp4")
        assert(result.fileSize == 400000L)
        assert(result.range == "bytes 100000-399999/400000")

        verifySequence {
            mapper.assetLinkParamsToCacheObject(assetParams)
            storageService.getFileProperties(cacheObject)
            storageService.getObjectChunkStream(
                cacheObject,
                399999,
                100000,
                false
            )
        }
    }

    @Test
    fun testGetAssetReturnsChunkWithDefaultSizeIfEndSmallerStart() {
        // Arrange
        val assetParams = mockk<AssetLinkParams>()
        val cacheObject = mockk<CacheObject>()
        val fileDetails = CachedObjectDetails(
            size = 4000000,
            mimeType = "video/mp4",
        )
        val stream = mockk<InputStream>()
        val range = "bytes=100000-4"

        every { mapper.assetLinkParamsToCacheObject(assetParams) } returns cacheObject
        every { storageService.getFileProperties(cacheObject) } returns fileDetails
        every { storageService.getObjectChunkStream(
            cacheObject,
            2100000,
            100000,
            false
        )
        } returns stream

        // Act
        val result = underTest.getAsset(assetParams, range)

        // Assert
        assert(result.stream == stream)
        assert(result.mimeType == "video/mp4")
        assert(result.fileSize == 4000000L)
        assert(result.range == "bytes 100000-2100000/4000000")

        verifySequence {
            mapper.assetLinkParamsToCacheObject(assetParams)
            storageService.getFileProperties(cacheObject)
            storageService.getObjectChunkStream(
                cacheObject,
                2100000,
                100000,
                false
            )
        }
    }
    /**
    @Test
    fun testGetStaticAssetReturnsFullStreamIfNoRangeSet() {
        // Arrange
        val fileDetails = CachedObjectDetails(
            size = 3L,
            mimeType = "application/pdf",
        )
        val stream = mockk<InputStream>()
        val request = mockk<HttpServletRequest>()

        every {request.requestURI} returns "blala/static/myuri"
        every { storageService.getFileProperties("file-eduhtml", "myuri") } returns fileDetails
        every { storageService.getObjectStream("file-eduhtml", "myuri") } returns stream

        excludeRecords { request.requestURI }

        // Act
        val result = underTest.getStaticAsset(request, "", "node123")

        // Assert
        assert(result.stream == stream)
        assert(result.mimeType == "application/pdf")
        assert(result.fileSize == 3L)

        verifySequence {
            storageService.getFileProperties("file-eduhtml", "myuri")
            storageService.getObjectStream("file-eduhtml", "myuri")
        }
    }

    @Test
    fun testGetStaticAssetReturnsChunkIfRangeSet() {
        // Arrange
        val fileDetails = CachedObjectDetails(
            size = 4000000,
            mimeType = "video/mp4",
        )
        val stream = mockk<InputStream>()
        val request = mockk<HttpServletRequest>()
        val range = "bytes=100000-400000"

        every {request.requestURI} returns "blala/static/myuri"
        every { storageService.getFileProperties("file-eduhtml", "myuri") } returns fileDetails
        every { storageService.getObjectChunkStream("file-eduhtml", "myuri", 100000, 400000) } returns stream

        excludeRecords { request.requestURI }

        // Act
        val result = underTest.getStaticAsset(request, range, "node123")

        // Assert
        assert(result.stream == stream)
        assert(result.mimeType == "video/mp4")
        assert(result.fileSize == 4000000L)
        assert(result.range == "bytes 100000-400000/4000000")

        verifySequence {
            storageService.getFileProperties("file-eduhtml", "myuri")
            storageService.getObjectChunkStream("file-eduhtml", "myuri", 100000, 400000)        }
    }
 */
}
