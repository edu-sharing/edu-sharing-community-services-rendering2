package org.edu_sharing.rendering.blobStorage

import com.fasterxml.jackson.databind.ObjectMapper
import io.minio.*
import io.minio.errors.ErrorResponseException
import io.minio.messages.ErrorResponse
import io.mockk.*
import io.mockk.junit5.MockKExtension
import okhttp3.Response
import org.apache.commons.codec.binary.Base64
import org.edu_sharing.rendering.blobStorage.minio.bucket.BucketStrategy
import org.edu_sharing.rendering.blobStorage.minio.MinioStorageService
import org.edu_sharing.rendering.config.MinioAdminClientProvider
import org.edu_sharing.rendering.dto.AssetLinkParams
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.exception.ResourceNotFoundException
import org.edu_sharing.rendering.repository.mongo.TrackingEntryRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLDecoder


@ExtendWith(MockKExtension::class)
class MinioStorageServiceTest {
    private val client = mockk<MinioClient>()
    private val trackingService = mockk<TrackingService>()
    private val adminClient = mockk<MinioAdminClientProvider>()
    private val bucketStrategy = mockk<BucketStrategy>()

    lateinit var underTest: MinioStorageService

    @BeforeEach
    fun setup() {
        underTest = MinioStorageService(client, adminClient, bucketStrategy, trackingService)
        underTest.publicUrl = "http://public"
        underTest.port = "8909"
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun testPutObjectCallsClientWithCorrectParams() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "audio",
            hash = "abc123",
            mimeType = "audio/wav",
            quality = 200,
            size = 145,
        )

        val metadata = mapOf("test" to "testvalue")
        val stream = "123".byteInputStream()
        val argumentSlot = slot<PutObjectArgs>()
        val bucketCheckSlot = slot<BucketExistsArgs>()

        every { client.putObject(capture(argumentSlot)) } returns mockk<ObjectWriteResponse>()
        every { client.bucketExists(capture(bucketCheckSlot)) } returns true

        // Act
        underTest.putObject(cacheObject, stream, metadata)

        // Assert
        val arguments = argumentSlot.captured
        val bucketArguments = bucketCheckSlot.captured

        assert(bucketArguments.bucket() == "audio")
        assert(arguments.stream().readAllBytes().toString(Charsets.UTF_8) == "123")
        assert(arguments.objectSize() == 145L)
        assert(arguments.partSize() == 145L)
        assert(arguments.partCount() == 1)
        assert(arguments.`object`() == "123/abc123_200.wav")
        assert(arguments.bucket() == "audio")
        assert(arguments.contentType() == "audio/wav")
        val capturedMetadata = arguments.userMetadata()
        assert(capturedMetadata.size() == 1)
        capturedMetadata.forEach { t, u ->
            assert(t.contains("test"))
            assert(u == "testvalue")
        }

        verifySequence {
            client.bucketExists(any())
            client.putObject(any())
        }
    }

    @Test
    fun testPutObjectCallsClientWithCorrectParamsIfMimeTypeAndSizeAreNotProvided() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "audio",
            hash = "abc123",
            quality = null,
            size = -1,
        )

        val stream = "123".byteInputStream()
        val argumentSlot = slot<PutObjectArgs>()
        val bucketCheckSlot = slot<BucketExistsArgs>()
        val bucketCreateSlot = slot<MakeBucketArgs>()

        every { client.putObject(capture(argumentSlot)) } returns mockk<ObjectWriteResponse>()
        every { client.bucketExists(capture(bucketCheckSlot)) } returns false
        justRun { client.makeBucket(capture(bucketCreateSlot)) }

        // Act
        underTest.putObject(cacheObject, stream)

        // Assert
        val arguments = argumentSlot.captured
        val bucketArguments = bucketCheckSlot.captured
        val bucketCreateArgs = bucketCreateSlot.captured

        assert(bucketArguments.bucket() == "audio")
        assert(bucketCreateArgs.bucket() == "audio")
        assert(arguments.objectSize() == -1L)
        assert(arguments.partSize() == underTest.defaultChunkSize)
        assert(arguments.contentType() == "application/octet-stream")
        assert(arguments.stream().readAllBytes().toString(Charsets.UTF_8) == "123")
        assert(arguments.`object`() == "123/abc123")
        assert(arguments.bucket() == "audio")
        val capturedMetadata = arguments.userMetadata()
        assert(capturedMetadata.size() == 0)

        verifySequence {
            client.bucketExists(any())
            client.makeBucket(capture(bucketCreateSlot))
            client.putObject(any())
        }
    }

    @Test
    fun testGetObjectLinkCreatesCorrectLink() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = 200,
            mimeType = "video/mp4"
        )
        val metadata = mapOf(
            "width" to "500",
            "height" to "200",
            "isHighestResolution" to "true"
        )
        val statResponse = mockk<StatObjectResponse>()
        val statSlot = slot<StatObjectArgs>()

        every { client.statObject(capture(statSlot)) } returns statResponse
        every { statResponse.userMetadata() } returns metadata

        // Act
        val result = underTest.getObjectLink(cacheObject)

        // Assert
        val statArgs = statSlot.captured
        assert(statArgs.`object`() == "123/abc123_200.mp4")
        assert(statArgs.bucket() == "video")
        assert(result.isHighestQuality)
        assert(result.width == 500)
        assert(result.height == 200)

        val uri = UriComponentsBuilder.fromUriString(result.link).build()
        val queryParams = uri.queryParams.toSingleValueMap()
        assert(queryParams.size == 1)
        val encodedParams = queryParams["assetParams"] ?: ""
        assert(encodedParams.isNotBlank())
        val decoded = Base64().decode(URLDecoder.decode(encodedParams, Charsets.UTF_8)).decodeToString()
        val assetLinkParams = ObjectMapper().readValue(decoded, AssetLinkParams::class.java)
        assert(assetLinkParams.hash == cacheObject.hash)
        assert(assetLinkParams.mimeType == cacheObject.mimeType)
        assert(assetLinkParams.quality == cacheObject.quality)
        assert(assetLinkParams.type == cacheObject.type)
        assert(assetLinkParams.nodeId == cacheObject.nodeId)

        verifySequence {
            client.statObject(any())
            statResponse.userMetadata()
        }
    }

    @Test
    fun testGetObjectLinkCreatesCorrectLinkIfQualityIsNullAndNoMetadataPresent() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = null,
            mimeType = "video/mp4"
        )
        val metadata = mapOf("width" to "a", "height" to "b", "isHighestResolution" to "c")
        val statResponse = mockk<StatObjectResponse>()
        val statSlot = slot<StatObjectArgs>()

        every { client.statObject(capture(statSlot)) } returns statResponse
        every { statResponse.userMetadata() } returns metadata

        // Act
        val result = underTest.getObjectLink(cacheObject)

        // Assert
        val statArgs = statSlot.captured
        assert(statArgs.`object`() == "123/abc123.mp4")
        assert(statArgs.bucket() == "video")
        assert(!result.isHighestQuality)
        assert(result.width == 0)
        assert(result.height == 0)

        val uri = UriComponentsBuilder.fromUriString(result.link).build()
        val queryParams = uri.queryParams.toSingleValueMap()
        assert(queryParams.size == 1)
        val encodedParams = queryParams["assetParams"] ?: ""
        assert(encodedParams.isNotBlank())
        val decoded = Base64().decode(URLDecoder.decode(encodedParams, Charsets.UTF_8)).decodeToString()
        val assetLinkParams = ObjectMapper().readValue(decoded, AssetLinkParams::class.java)
        assert(assetLinkParams.hash == cacheObject.hash)
        assert(assetLinkParams.mimeType == cacheObject.mimeType)
        assert(assetLinkParams.quality == 0)
        assert(assetLinkParams.type == cacheObject.type)
        assert(assetLinkParams.nodeId == cacheObject.nodeId)

        verifySequence {
            client.statObject(any())
            statResponse.userMetadata()
        }
    }

    @Test
    fun testGetObjectLinkCreatesCorrectLinkIfQualityIsNullAndMetadataIsCorrupted() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = null,
            mimeType = "video/mp4"
        )
        val metadata = emptyMap<String, String>()
        val statResponse = mockk<StatObjectResponse>()
        val statSlot = slot<StatObjectArgs>()

        every { client.statObject(capture(statSlot)) } returns statResponse
        every { statResponse.userMetadata() } returns metadata

        // Act
        val result = underTest.getObjectLink(cacheObject)

        // Assert
        val statArgs = statSlot.captured
        assert(statArgs.`object`() == "123/abc123.mp4")
        assert(statArgs.bucket() == "video")
        assert(!result.isHighestQuality)
        assert(result.width == 0)
        assert(result.height == 0)

        val uri = UriComponentsBuilder.fromUriString(result.link).build()
        val queryParams = uri.queryParams.toSingleValueMap()
        assert(queryParams.size == 1)
        val encodedParams = queryParams["assetParams"] ?: ""
        assert(encodedParams.isNotBlank())
        val decoded = Base64().decode(URLDecoder.decode(encodedParams, Charsets.UTF_8)).decodeToString()
        val assetLinkParams = ObjectMapper().readValue(decoded, AssetLinkParams::class.java)
        assert(assetLinkParams.hash == cacheObject.hash)
        assert(assetLinkParams.mimeType == cacheObject.mimeType)
        assert(assetLinkParams.quality == 0)
        assert(assetLinkParams.type == cacheObject.type)
        assert(assetLinkParams.nodeId == cacheObject.nodeId)

        verifySequence {
            client.statObject(any())
            statResponse.userMetadata()
        }
    }

    @Test
    fun testGetObjectLinkCreatesCorrectLinkIfQualityIsNullAndMetadataHasOnlyNullValues() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = null,
            mimeType = "video/mp4"
        )
        val metadata = mapOf("width" to null, "height" to null, "isHighestResolution" to null)

        val statResponse = mockk<StatObjectResponse>()
        val statSlot = slot<StatObjectArgs>()

        every { client.statObject(capture(statSlot)) } returns statResponse
        every { statResponse.userMetadata() } returns metadata

        // Act
        val result = underTest.getObjectLink(cacheObject)

        // Assert
        val statArgs = statSlot.captured
        assert(statArgs.`object`() == "123/abc123.mp4")
        assert(statArgs.bucket() == "video")
        assert(!result.isHighestQuality)
        assert(result.width == 0)
        assert(result.height == 0)

        val uri = UriComponentsBuilder.fromUriString(result.link).build()
        val queryParams = uri.queryParams.toSingleValueMap()
        assert(queryParams.size == 1)
        val encodedParams = queryParams["assetParams"] ?: ""
        assert(encodedParams.isNotBlank())
        val decoded = Base64().decode(URLDecoder.decode(encodedParams, Charsets.UTF_8)).decodeToString()
        val assetLinkParams = ObjectMapper().readValue(decoded, AssetLinkParams::class.java)
        assert(assetLinkParams.hash == cacheObject.hash)
        assert(assetLinkParams.mimeType == cacheObject.mimeType)
        assert(assetLinkParams.quality == 0)
        assert(assetLinkParams.type == cacheObject.type)
        assert(assetLinkParams.nodeId == cacheObject.nodeId)

        verifySequence {
            client.statObject(any())
            statResponse.userMetadata()
        }
    }

    @Test
    fun testGetObjectLinkThrowsExceptionIfClientThrowsException() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = null,
            mimeType = "video/mp4"
        )
        val fakeResponse = mockk<ErrorResponse>()
        every { fakeResponse.message() } returns ""
        every { client.statObject(any()) } throws ErrorResponseException(fakeResponse, mockk<Response>(), "")

        // Act
        assertThrows<ResourceNotFoundException> { underTest.getObjectLink(cacheObject) }

        // Assert

        verifySequence {
            client.statObject(any())
        }
    }

    @Test
    fun testGetObjectLinkReturnsStaticLinkIfPathProvided() {
        // Arrange
        val path = "/my/super/path/file.css"

        // Act
        val result = underTest.getObjectLink(path)

        // Assert
        assert(result.link == "http://public:8909/public/asset/static/my/super/path/file.css")
    }

    @Test
    fun testRemoveObjectCallsClientWithCorrectParams() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = 100,
            mimeType = "video/mp4"
        )

        val argumentSlot = slot<RemoveObjectArgs>()
        justRun { client.removeObject(capture(argumentSlot)) }

        // Act
        underTest.removeObject(cacheObject)

        // Assert
        val arguments = argumentSlot.captured
        assert(arguments.`object`() == "123/abc123_100.mp4")
        assert(arguments.bucket() == "video")

        verify(exactly = 1) {
            client.removeObject(capture(argumentSlot))
        }
    }

    @Test
    fun testRemoveObjectCallsClientWithCorrectParamsForTempFiles() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = 100,
            mimeType = "video/mp4"
        )

        val argumentSlot = slot<RemoveObjectArgs>()
        justRun { client.removeObject(capture(argumentSlot)) }

        // Act
        underTest.removeObject(cacheObject, true)

        // Assert
        val arguments = argumentSlot.captured
        assert(arguments.bucket() == "temp")
        assert(arguments.`object`() == "video/123/abc123.mp4")
        verify(exactly = 1) {
            client.removeObject(capture(argumentSlot))
        }
    }

    @Test
    fun testGetObjectStreamCallsClientWithCorrectParamsAndReturnsStream() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = 100,
            mimeType = "video/mp4"
        )

        val argumentSlot = slot<GetObjectArgs>()
        val stream = mockk<GetObjectResponse>()
        every { client.getObject(capture(argumentSlot)) } returns stream

        // Act
        val result = underTest.getObjectStream(cacheObject)

        // Assert
        assert(result == stream)
        val arguments = argumentSlot.captured
        assert(arguments.bucket() == "video")
        assert(arguments.`object`() == "123/abc123_100.mp4")

        verify(exactly = 1) {
            client.getObject(any())
        }
        confirmVerified(client)
    }

    @Test
    fun testGetObjectStreamCallsClientWithCorrectParamsAndReturnsStreamForTempFile() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = 100,
            mimeType = "video/mp4"
        )

        val argumentSlot = slot<GetObjectArgs>()
        val stream = mockk<GetObjectResponse>()
        every { client.getObject(capture(argumentSlot)) } returns stream

        // Act
        val result = underTest.getObjectStream(cacheObject, true)

        // Assert
        assert(result == stream)
        val arguments = argumentSlot.captured
        assert(arguments.bucket() == "temp")
        assert(arguments.`object`() == "video/123/abc123.mp4")

        verify(exactly = 1) {
            client.getObject(any())
        }
        confirmVerified(client)
    }

    @Test
    fun testGetObjectStreamCallsClientWithCorrectParamsForStaticLink() {
        // Arrange
        val bucket = "mybucket"
        val path = "mypath"

        val argumentSlot = slot<GetObjectArgs>()
        val stream = mockk<GetObjectResponse>()
        every { client.getObject(capture(argumentSlot)) } returns stream

        // Act
        val result = underTest.getObjectStream(bucket, path)

        // Assert
        assert(result == stream)
        val arguments = argumentSlot.captured
        assert(arguments.`object`() == path)
        assert(arguments.bucket() == bucket)

        verify(exactly = 1) {
            client.getObject(any())
        }
        confirmVerified(client)
    }

    @Test
    fun testGetObjectChunkStreamCallsClientWithCorrectParamsAndReturnsStream() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = 100,
            mimeType = "video/mp4"
        )

        val argumentSlot = slot<GetObjectArgs>()
        val stream = mockk<GetObjectResponse>()
        every { client.getObject(capture(argumentSlot)) } returns stream

        // Act
        val result = underTest.getObjectChunkStream(cacheObject, 2, 1, false)

        // Assert
        assert(result == stream)
        val arguments = argumentSlot.captured
        assert(arguments.`object`() == "123/abc123_100.mp4")
        assert(arguments.bucket() == "video")
        assert(arguments.offset() == 1L)
        assert(arguments.length() == 2L)

        verify(exactly = 1) {
            client.getObject(any())
        }
        confirmVerified(client)
    }

    @Test
    fun testGetObjectChunkStreamCallsClientWithCorrectParamsAndReturnsStreamForTempFile() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = 100,
            mimeType = "video/mp4"
        )

        val argumentSlot = slot<GetObjectArgs>()
        val stream = mockk<GetObjectResponse>()
        every { client.getObject(capture(argumentSlot)) } returns stream

        // Act
        val result = underTest.getObjectChunkStream(cacheObject, 2, 1, true)

        // Assert
        assert(result == stream)
        val arguments = argumentSlot.captured
        assert(arguments.`object`() == "video/123/abc123.mp4")
        assert(arguments.bucket() == "temp")
        assert(arguments.offset() == 1L)
        assert(arguments.length() == 2L)

        verify(exactly = 1) {
            client.getObject(any())
        }
        confirmVerified(client)
    }

    @Test
    fun testGetObjectChunkStreamCallsClientWithCorrectParamsAndReturnsStreamForStaticLink() {
        // Arrange
        val bucket = "mybucket"
        val path = "mypath"
        val argumentSlot = slot<GetObjectArgs>()
        val stream = mockk<GetObjectResponse>()
        every { client.getObject(capture(argumentSlot)) } returns stream

        // Act
        val result = underTest.getObjectChunkStream(bucket, path, 1, 2)

        // Assert
        assert(result == stream)
        val arguments = argumentSlot.captured
        assert(arguments.`object`() == path)
        assert(arguments.bucket() == bucket)
        assert(arguments.offset() == 1L)
        assert(arguments.length() == 2L)

        verify(exactly = 1) {
            client.getObject(any())
        }
        confirmVerified(client)
    }

    @Test
    fun testPutTempFileCallsClientWithCorrectParams() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = 100,
            mimeType = "video/mp4",
            size = 12222
        )
        val argumentSlot = slot<PutObjectArgs>()
        val stream = "1234".byteInputStream()

        every { client.bucketExists(any()) } returns true
        every { client.putObject(capture(argumentSlot)) } returns mockk<ObjectWriteResponse>()

        // Act
        underTest.putTempFile(cacheObject, stream)

        // Assert
        val arguments = argumentSlot.captured
        assert(arguments.stream().readAllBytes().toString(Charsets.UTF_8) == "1234")
        assert(arguments.bucket() == "temp")
        assert(arguments.`object`() == "video/123/abc123.mp4")
        assert(arguments.contentType() == "video/mp4")
        assert(arguments.objectSize() == 12222L)

        verifySequence {
            client.bucketExists(any())
            client.putObject(capture(argumentSlot))
        }
    }

    @Test
    fun testPutTempFileCallsClientWithCorrectParamsIfSizeNotSet() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = 100,
            mimeType = "video/mp4",
            size = -1
        )
        val argumentSlot = slot<PutObjectArgs>()
        val stream = "1234".byteInputStream()

        every { client.bucketExists(any()) } returns true
        every { client.putObject(capture(argumentSlot)) } returns mockk<ObjectWriteResponse>()

        // Act
        underTest.putTempFile(cacheObject, stream)

        // Assert
        val arguments = argumentSlot.captured
        assert(arguments.stream().readAllBytes().toString(Charsets.UTF_8) == "1234")
        assert(arguments.bucket() == "temp")
        assert(arguments.`object`() == "video/123/abc123.mp4")
        assert(arguments.contentType() == "video/mp4")
        assert(arguments.objectSize() == -1L)
        assert(arguments.partSize() == underTest.defaultChunkSize)

        verifySequence {
            client.bucketExists(any())
            client.putObject(capture(argumentSlot))
        }
    }

    @Test
    fun testGetFilePropertiesCorrectlyMapsStatsReturnedByClient() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = 100,
            mimeType = "video/mp4",
            size = 1233
        )

        val statResponse = mockk<StatObjectResponse>()
        every { statResponse.size() } returns 12345
        every { statResponse.contentType() } returns "video/mp3"
        every { client.statObject(any()) } returns statResponse

        excludeRecords {
            statResponse.size()
            statResponse.contentType()
        }

        // Act
        val result = underTest.getFileProperties(cacheObject)

        // Assert
        assert(result.size == 12345L)
        assert(result.mimeType == "video/mp3")

        verify (exactly = 1) {client.statObject(any())}
        confirmVerified(client)
    }

    @Test
    fun testGetFilePropertiesThrowsExceptionIfClientThrowsException() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "video",
            hash = "abc123",
            quality = 100,
            mimeType = "video/mp4",
            size = 1233
        )

        every { client.statObject(any()) } throws Exception("")

        // Act
        assertThrows<ResourceNotFoundException> { underTest.getFileProperties(cacheObject) }

        verify (exactly = 1) {client.statObject(any())}
        confirmVerified(client)
    }

    @Test
    fun testGetFilePropertiesCorrectlyMapsStatsReturnedByClientForStaticObject() {
        // Arrange
        val path = "mypath"
        val bucket = "mybucket"

        val statResponse = mockk<StatObjectResponse>()
        every { statResponse.size() } returns 12345
        every { statResponse.contentType() } returns "video/mp3"
        every { client.statObject(any()) } returns statResponse

        excludeRecords {
            statResponse.size()
            statResponse.contentType()
        }

        // Act
        val result = underTest.getFileProperties(bucket, path)

        // Assert
        assert(result.size == 12345L)
        assert(result.mimeType == "video/mp3")

        verify (exactly = 1) {client.statObject(any())}
        confirmVerified(client)
    }

    @Test
    fun testGetFilePropertiesThrowsExceptionIfClientThrowsExceptionForStaticObject() {
        // Arrange
        val path = "mypath"
        val bucket = "mybucket"

        every { client.statObject(any()) } throws Exception("")

        // Act
        assertThrows<ResourceNotFoundException> { underTest.getFileProperties(bucket, path) }

        verify (exactly = 1) {client.statObject(any())}
        confirmVerified(client)
    }
}
