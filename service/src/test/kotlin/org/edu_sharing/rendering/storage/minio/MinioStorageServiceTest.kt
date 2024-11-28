package org.edu_sharing.rendering.storage.minio

import com.fasterxml.jackson.databind.ObjectMapper
import io.minio.*
import io.minio.errors.ErrorResponseException
import io.minio.messages.ErrorResponse
import io.mockk.*
import io.mockk.junit5.MockKExtension
import okhttp3.Response
import org.apache.commons.codec.binary.Base64
import org.edu_sharing.rendering.asset.dto.AssetLinkParams
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.storage.minio.bucket.BucketStrategy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.MediaType
import org.springframework.web.util.UriComponentsBuilder
import java.io.ByteArrayInputStream
import java.net.URLDecoder


@ExtendWith(MockKExtension::class)
class MinioStorageServiceTest {
    private val client = mockk<MinioClient>()
    private val trackingService = mockk<TrackingService>()
    private val adminClient = mockk<MinioAdminClientProvider>()
    private val bucketStrategy = mockk<BucketStrategy>()
    private val appInfo = AppInfo()

    lateinit var underTest: MinioStorageService

    @BeforeEach
    fun setup() {
        appInfo.public = AppInfo.ConnectionInfo("http", "public", 8909, "http://public:8909" )
        underTest = MinioStorageService(client, adminClient, bucketStrategy, trackingService, appInfo)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun testPutObjectCallsClientWithCorrectParams() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val inputStream = ByteArrayInputStream("abc123".toByteArray())
        val metadata = mapOf("test" to "testValue")
        val bucketArgumentsSlot = slot<BucketExistsArgs>()
        val objectArgumentSlot = slot<PutObjectArgs>()

        every { bucketStrategy.getStoragePath(cacheObject) } returns "somepath"
        every { bucketStrategy.getBucket(cacheObject) } returns "targetbucket"
        every { client.bucketExists(capture(bucketArgumentsSlot)) } returns true
        every { client.putObject(capture(objectArgumentSlot)) } returns mockk<ObjectWriteResponse>()
        justRun { trackingService.trackCacheObject(cacheObject, "targetbucket") }

        every { cacheObject.size } returns 123
        every { cacheObject.mimeType } returns "application/json"

        excludeRecords {
            cacheObject.size
            cacheObject.mimeType
        }

        // Act
        underTest.putObject(cacheObject, inputStream, metadata)

        // Assert
        assert(bucketArgumentsSlot.isCaptured)
        val capturedBucketArgs = bucketArgumentsSlot.captured
        assert(capturedBucketArgs.bucket() == "targetbucket")

        assert(objectArgumentSlot.isCaptured)
        val capturedObjectArgs = objectArgumentSlot.captured
        assert(capturedObjectArgs.bucket() == "targetbucket")
        assert(capturedObjectArgs.stream().readAllBytes().toString(Charsets.UTF_8) == "abc123")
        assert(capturedObjectArgs.`object`() == "somepath")
        assert(capturedObjectArgs.contentType() == "application/json")
        val writtenMetadata = capturedObjectArgs.userMetadata()
        assert(writtenMetadata.size() == 1)
        assert(writtenMetadata.get("x-amz-meta-test").first() == "testValue")

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject)
            bucketStrategy.getBucket(cacheObject)
            client.bucketExists(capture(bucketArgumentsSlot))
            client.putObject(capture(objectArgumentSlot))
            trackingService.trackCacheObject(cacheObject, "targetbucket")
        }
    }

    @Test
    fun testPutObjectCallsClientWithCorrectParamsWithoutMetadataAndContentType() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val inputStream = ByteArrayInputStream("abc123".toByteArray())
        val bucketArgumentsSlot = slot<BucketExistsArgs>()
        val objectArgumentSlot = slot<PutObjectArgs>()

        every { bucketStrategy.getStoragePath(cacheObject) } returns "somepath"
        every { bucketStrategy.getBucket(cacheObject) } returns "targetbucket"
        every { client.bucketExists(capture(bucketArgumentsSlot)) } returns true
        every { client.putObject(capture(objectArgumentSlot)) } returns mockk<ObjectWriteResponse>()
        justRun { trackingService.trackCacheObject(cacheObject, "targetbucket") }

        every { cacheObject.size } returns 123
        every { cacheObject.mimeType } returns ""

        excludeRecords {
            cacheObject.size
            cacheObject.mimeType
        }

        // Act
        underTest.putObject(cacheObject, inputStream)

        // Assert
        assert(bucketArgumentsSlot.isCaptured)
        val capturedBucketArgs = bucketArgumentsSlot.captured
        assert(capturedBucketArgs.bucket() == "targetbucket")

        assert(objectArgumentSlot.isCaptured)
        val capturedObjectArgs = objectArgumentSlot.captured
        assert(capturedObjectArgs.bucket() == "targetbucket")
        assert(capturedObjectArgs.stream().readAllBytes().toString(Charsets.UTF_8) == "abc123")
        assert(capturedObjectArgs.`object`() == "somepath")
        assert(capturedObjectArgs.contentType() == "application/octet-stream")
        val writtenMetadata = capturedObjectArgs.userMetadata()
        assert(writtenMetadata.size() == 0)

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject)
            bucketStrategy.getBucket(cacheObject)
            client.bucketExists(capture(bucketArgumentsSlot))
            client.putObject(capture(objectArgumentSlot))
            trackingService.trackCacheObject(cacheObject, "targetbucket")
        }
    }

    @Test
    fun putObjectStaticCallsClientWithCorrectArguments() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val inputStream = ByteArrayInputStream("abc123".toByteArray())
        val metadata = mapOf("test" to "testValue")
        val bucketArgumentsSlot = slot<BucketExistsArgs>()
        val objectArgumentSlot = slot<PutObjectArgs>()

        every { bucketStrategy.getStoragePath(cacheObject, "inputpath") } returns "somepath"
        every { bucketStrategy.getBucket(cacheObject) } returns "targetbucket"
        every { client.bucketExists(capture(bucketArgumentsSlot)) } returns true
        every { client.putObject(capture(objectArgumentSlot)) } returns mockk<ObjectWriteResponse>()
        justRun { trackingService.trackCacheObject(cacheObject, "targetbucket") }

        every { cacheObject.size } returns 123
        every { cacheObject.mimeType } returns "application/json"

        excludeRecords {
            cacheObject.size
            cacheObject.mimeType
        }

        // Act
        underTest.putObject(cacheObject, inputStream, "inputpath", metadata)

        // Assert
        assert(bucketArgumentsSlot.isCaptured)
        val capturedBucketArgs = bucketArgumentsSlot.captured
        assert(capturedBucketArgs.bucket() == "targetbucket")

        assert(objectArgumentSlot.isCaptured)
        val capturedObjectArgs = objectArgumentSlot.captured
        assert(capturedObjectArgs.bucket() == "targetbucket")
        assert(capturedObjectArgs.stream().readAllBytes().toString(Charsets.UTF_8) == "abc123")
        assert(capturedObjectArgs.`object`() == "somepath")
        assert(capturedObjectArgs.contentType() == "application/json")
        val writtenMetadata = capturedObjectArgs.userMetadata()
        assert(writtenMetadata.size() == 1)
        assert(writtenMetadata.get("x-amz-meta-test").first() == "testValue")

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject, "inputpath")
            bucketStrategy.getBucket(cacheObject)
            client.bucketExists(capture(bucketArgumentsSlot))
            client.putObject(capture(objectArgumentSlot))
            trackingService.trackCacheObject(cacheObject, "targetbucket")
        }
    }

    @Test
    fun testPutObjectStaticCallsClientWithCorrectParamsWithoutMetadataAndContentTypeAndCreatesMissingBucket() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val inputStream = ByteArrayInputStream("abc123".toByteArray())
        val bucketArgumentsSlot = slot<BucketExistsArgs>()
        val makeBucketArgumentSlot = slot<MakeBucketArgs>()
        val objectArgumentSlot = slot<PutObjectArgs>()

        every { bucketStrategy.getStoragePath(cacheObject, "inputpath") } returns "somepath"
        every { bucketStrategy.getBucket(cacheObject) } returns "targetbucket"
        every { client.bucketExists(capture(bucketArgumentsSlot)) } returns false
        justRun { client.makeBucket(capture(makeBucketArgumentSlot)) }

        every { client.putObject(capture(objectArgumentSlot)) } returns mockk<ObjectWriteResponse>()
        justRun { trackingService.trackCacheObject(cacheObject, "targetbucket") }

        every { cacheObject.size } returns -1
        every { cacheObject.mimeType } returns ""

        excludeRecords {
            cacheObject.size
            cacheObject.mimeType
        }

        // Act
        underTest.putObject(cacheObject, inputStream, "inputpath")

        // Assert
        assert(bucketArgumentsSlot.isCaptured)
        val capturedBucketArgs = bucketArgumentsSlot.captured
        assert(capturedBucketArgs.bucket() == "targetbucket")

        assert(makeBucketArgumentSlot.isCaptured)
        val capturedMakeBucketArgs = makeBucketArgumentSlot.captured
        assert(capturedMakeBucketArgs.bucket() == "targetbucket")

        assert(objectArgumentSlot.isCaptured)
        val capturedObjectArgs = objectArgumentSlot.captured
        assert(capturedObjectArgs.bucket() == "targetbucket")
        assert(capturedObjectArgs.stream().readAllBytes().toString(Charsets.UTF_8) == "abc123")
        assert(capturedObjectArgs.`object`() == "somepath")
        assert(capturedObjectArgs.contentType() == "application/octet-stream")
        val writtenMetadata = capturedObjectArgs.userMetadata()
        assert(writtenMetadata.size() == 0)

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject, "inputpath")
            bucketStrategy.getBucket(cacheObject)
            client.bucketExists(capture(bucketArgumentsSlot))
            client.makeBucket(capture(makeBucketArgumentSlot))
            client.putObject(capture(objectArgumentSlot))
            trackingService.trackCacheObject(cacheObject, "targetbucket")
        }
    }

    @Test
    fun testGetObjectLinkCreatesCorrectLinkWithoutQualityAndSetMetadata() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "file-pdf",
            hash = "abc123",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123"
        )
        val statObjectArgsSlot = slot<StatObjectArgs>()
        val statObjectResponse = mockk<StatObjectResponse>()

        every { bucketStrategy.getStoragePath(cacheObject) } returns "targetpath"
        every { bucketStrategy.getBucket(cacheObject) } returns "targetbucket"
        every { client.statObject(capture(statObjectArgsSlot)) } returns statObjectResponse
        every { statObjectResponse.userMetadata() } returns emptyMap()

        excludeRecords {
            statObjectResponse.userMetadata()
        }

        // Act
        val result = underTest.getObjectLink(cacheObject)

        // Assert
        assert(statObjectArgsSlot.isCaptured)
        assert(statObjectArgsSlot.captured.bucket() == "targetbucket")
        assert(statObjectArgsSlot.captured.`object`() == "targetpath")

        val uri = UriComponentsBuilder.fromUriString(result.link).build()
        val queryParams = uri.queryParams.toSingleValueMap()
        assert(queryParams.size == 1)
        val encodedParams = queryParams["assetParams"] ?: ""
        assert(encodedParams.isNotBlank())
        val decoded = Base64().decode(URLDecoder.decode(encodedParams, Charsets.UTF_8)).decodeToString()
        val assetLinkParams = ObjectMapper().readValue(decoded, AssetLinkParams::class.java)
        assert(assetLinkParams.repoId == "repoId123")
        assert(assetLinkParams.nodeId == "123")
        assert(assetLinkParams.hash == "abc123")
        assert(assetLinkParams.quality == 0)
        assert(assetLinkParams.mimeType == "application/pdf")
        assert(assetLinkParams.type == "file-pdf")
        assert(result.height == 0)
        assert(result.width == 0)
        assert(result.isHighestQuality == false)

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject)
            bucketStrategy.getBucket(cacheObject)
            client.statObject(capture(statObjectArgsSlot))
        }
    }

    @Test
    fun testGetObjectLinkCreatesCorrectLinkWithQualityAndSetMetadata() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "file-pdf",
            hash = "abc123",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = 1
        )
        val statObjectArgsSlot = slot<StatObjectArgs>()
        val statObjectResponse = mockk<StatObjectResponse>()
        val metadata = mapOf(
            "width" to "2",
            "isHighestResolution" to "true",
            "height" to "4"
        )

        every { bucketStrategy.getStoragePath(cacheObject) } returns "targetpath"
        every { bucketStrategy.getBucket(cacheObject) } returns "targetbucket"
        every { client.statObject(capture(statObjectArgsSlot)) } returns statObjectResponse
        every { statObjectResponse.userMetadata() } returns metadata

        excludeRecords {
            statObjectResponse.userMetadata()
        }

        // Act
        val result = underTest.getObjectLink(cacheObject)

        // Assert
        assert(statObjectArgsSlot.isCaptured)
        assert(statObjectArgsSlot.captured.bucket() == "targetbucket")
        assert(statObjectArgsSlot.captured.`object`() == "targetpath")

        val uri = UriComponentsBuilder.fromUriString(result.link).build()
        val queryParams = uri.queryParams.toSingleValueMap()
        assert(queryParams.size == 1)
        val encodedParams = queryParams["assetParams"] ?: ""
        assert(encodedParams.isNotBlank())
        val decoded = Base64().decode(URLDecoder.decode(encodedParams, Charsets.UTF_8)).decodeToString()
        val assetLinkParams = ObjectMapper().readValue(decoded, AssetLinkParams::class.java)
        assert(assetLinkParams.repoId == "repoId123")
        assert(assetLinkParams.nodeId == "123")
        assert(assetLinkParams.hash == "abc123")
        assert(assetLinkParams.quality == 1)
        assert(assetLinkParams.mimeType == "application/pdf")
        assert(assetLinkParams.type == "file-pdf")
        assert(result.height == 4)
        assert(result.width == 2)
        assert(result.isHighestQuality == true)

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject)
            bucketStrategy.getBucket(cacheObject)
            client.statObject(capture(statObjectArgsSlot))
        }
    }

    @Test
    fun testGetObjectLinkCreatesCorrectLinkWithQualityAndFaultyMetadata() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "file-pdf",
            hash = "abc123",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = 1
        )
        val statObjectArgsSlot = slot<StatObjectArgs>()
        val statObjectResponse = mockk<StatObjectResponse>()
        val metadata = mapOf(
            "width" to "a",
            "isHighestResolution" to "b",
            "height" to "c"
        )

        every { bucketStrategy.getStoragePath(cacheObject) } returns "targetpath"
        every { bucketStrategy.getBucket(cacheObject) } returns "targetbucket"
        every { client.statObject(capture(statObjectArgsSlot)) } returns statObjectResponse
        every { statObjectResponse.userMetadata() } returns metadata

        excludeRecords {
            statObjectResponse.userMetadata()
        }

        // Act
        val result = underTest.getObjectLink(cacheObject)

        // Assert
        assert(statObjectArgsSlot.isCaptured)
        assert(statObjectArgsSlot.captured.bucket() == "targetbucket")
        assert(statObjectArgsSlot.captured.`object`() == "targetpath")

        val uri = UriComponentsBuilder.fromUriString(result.link).build()
        val queryParams = uri.queryParams.toSingleValueMap()
        assert(queryParams.size == 1)
        val encodedParams = queryParams["assetParams"] ?: ""
        assert(encodedParams.isNotBlank())
        val decoded = Base64().decode(URLDecoder.decode(encodedParams, Charsets.UTF_8)).decodeToString()
        val assetLinkParams = ObjectMapper().readValue(decoded, AssetLinkParams::class.java)
        assert(assetLinkParams.repoId == "repoId123")
        assert(assetLinkParams.nodeId == "123")
        assert(assetLinkParams.hash == "abc123")
        assert(assetLinkParams.quality == 1)
        assert(assetLinkParams.mimeType == "application/pdf")
        assert(assetLinkParams.type == "file-pdf")
        assert(result.height == 0)
        assert(result.width == 0)
        assert(result.isHighestQuality == false)

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject)
            bucketStrategy.getBucket(cacheObject)
            client.statObject(capture(statObjectArgsSlot))
        }
    }

    @Test
    fun testGetObjectLinkThrowsProperExceptionIfNotFoundInStorage() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "file-pdf",
            hash = "abc123",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = 1
        )

        val fakeResponse = mockk<ErrorResponse>()
        every { fakeResponse.message() } returns ""
        every { bucketStrategy.getStoragePath(cacheObject) } returns "targetpath"
        every { bucketStrategy.getBucket(cacheObject) } returns "targetbucket"
        every { client.statObject(any()) } throws ErrorResponseException(fakeResponse, mockk<Response>(), "")

        excludeRecords {
            fakeResponse.message()
        }
        // Act
        assertThrows<ResourceNotFoundException> { underTest.getObjectLink(cacheObject) }

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject)
            bucketStrategy.getBucket(cacheObject)
            client.statObject(any())
        }
    }

    @Test
    fun testGetObjectLinkStaticReturnsProperLink() {
        val cacheObject = mockk<CacheObject>()
        every { bucketStrategy.prefixStaticPath(cacheObject, "nonprefixedpath") } returns "/targetpath"

        val result = underTest.getObjectLink(cacheObject, "nonprefixedpath")

        val uri = UriComponentsBuilder.fromUriString(result.link).build()
        assert(uri.host == "public")
        assert(uri.port == 8909)
        assert(uri.scheme == "http")
        assert(uri.path == "/public/asset/static/targetpath")

        verify(exactly = 1) { underTest.getObjectLink(cacheObject, "nonprefixedpath") }

        confirmVerified(bucketStrategy)
    }

    @Test
    fun testGetCacheObjectFromStaticPathReturnsResultFromBucketStrategy() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val path = "somePath"
        val expected = Pair<CacheObject, String>(cacheObject, path)

        every { bucketStrategy.getCacheObjectFromStaticPath(path) } returns expected

        // Act
        val result = underTest.getCacheObjectFromStaticPath(path)

        // Assert
        assert(result == expected)

        verify(exactly = 1) { bucketStrategy.getCacheObjectFromStaticPath(path) }
        confirmVerified(bucketStrategy)
    }

    @Test
    fun testGetStoragePathReturnsResultFromBucketStrategy() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val path = "somePath"
        val expected = "someResult"

        every { bucketStrategy.getStoragePath(cacheObject, path) } returns expected

        // Act
        val result = underTest.getStoragePath(cacheObject, path)

        assert(result == expected)

        verify(exactly = 1) { bucketStrategy.getStoragePath(cacheObject, path) }
        confirmVerified(bucketStrategy)
    }

    @Test
    fun testRemoveObjectCallsClientWithProperArgumentsForNonTempFile() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val removeObjectArgsSlot = slot<RemoveObjectArgs>()

        every { bucketStrategy.getBucket(cacheObject) } returns "targetbucket"
        every { bucketStrategy.getStoragePath(cacheObject) } returns "targetpath"
        justRun { client.removeObject(capture(removeObjectArgsSlot)) }
        justRun { trackingService.deleteTrackedObject(cacheObject, "targetbucket") }

        // Act
        underTest.removeObject(cacheObject, false)

        // Assert
        assert(removeObjectArgsSlot.isCaptured)
        assert(removeObjectArgsSlot.captured.bucket() == "targetbucket")
        assert(removeObjectArgsSlot.captured.`object`() == "targetpath")

        verifySequence {
            bucketStrategy.getBucket(cacheObject)
            bucketStrategy.getStoragePath(cacheObject)
            client.removeObject(capture(removeObjectArgsSlot))
            trackingService.deleteTrackedObject(cacheObject, "targetbucket")
        }
    }

    @Test
    fun testRemoveObjectCallsClientWithProperArgumentsForTempFile() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "file-pdf",
            hash = "abc123",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = 1
        )

        val removeObjectArgsSlot = slot<RemoveObjectArgs>()

        every { bucketStrategy.getExtensionFromMimeType("application/pdf") } returns ".pdf"
        justRun { client.removeObject(capture(removeObjectArgsSlot)) }

        // Act
        underTest.removeObject(cacheObject, true)

        assert(removeObjectArgsSlot.isCaptured)
        assert(removeObjectArgsSlot.captured.`object`() == "file-pdf/123/abc123.pdf")
        assert(removeObjectArgsSlot.captured.bucket() == "temp")

        verifySequence {
            bucketStrategy.getExtensionFromMimeType("application/pdf")
            client.removeObject(capture(removeObjectArgsSlot))
        }
    }

    @Test
    fun testGetObjectStreamCallsClientWithCorrectArguments() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val getObjectArgsSlot = slot<GetObjectArgs>()
        val stream = mockk<GetObjectResponse>()

        every { bucketStrategy.getBucket(cacheObject) } returns "targetbucket"
        every { bucketStrategy.getStoragePath(cacheObject) } returns "targetpath"
        every { client.getObject(capture(getObjectArgsSlot)) } returns stream
        justRun { trackingService.trackCacheObject(cacheObject, "targetbucket") }

        // Act
        val result = underTest.getObjectStream(cacheObject, false)

        assert(result == stream)

        // Assert
        assert(getObjectArgsSlot.isCaptured)
        assert(getObjectArgsSlot.captured.bucket() == "targetbucket")
        assert(getObjectArgsSlot.captured.`object`() == "targetpath")

        verifySequence {
            bucketStrategy.getBucket(cacheObject)
            bucketStrategy.getStoragePath(cacheObject)
            client.getObject(capture(getObjectArgsSlot))
            trackingService.trackCacheObject(cacheObject, "targetbucket")
        }
    }

    @Test
    fun testGetObjectStreamCallsClientWithCorrectArgumentsForTempFile() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "file-pdf",
            hash = "abc123",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = 1
        )

        val getObjectArgsSlot = slot<GetObjectArgs>()
        val stream = mockk<GetObjectResponse>()

        every { bucketStrategy.getExtensionFromMimeType("application/pdf") } returns ".pdf"
        every { client.getObject(capture(getObjectArgsSlot)) } returns stream

        // Act
        val result = underTest.getObjectStream(cacheObject, true)
        assert(result == stream)

        assert(getObjectArgsSlot.isCaptured)
        assert(getObjectArgsSlot.captured.`object`() == "file-pdf/123/abc123.pdf")
        assert(getObjectArgsSlot.captured.bucket() == "temp")

        verifySequence {
            bucketStrategy.getExtensionFromMimeType("application/pdf")
            client.getObject(capture(getObjectArgsSlot))
        }
    }

    @Test
    fun testGetObjectStreamStaticCallsClientWithProperArguments() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val getObjectArgsSlot = slot<GetObjectArgs>()
        val stream = mockk<GetObjectResponse>()

        every { bucketStrategy.getBucket(cacheObject) } returns "targetbucket"
        every { bucketStrategy.getStoragePath(cacheObject, "testpath") } returns "targetpath"
        every { client.getObject(capture(getObjectArgsSlot)) } returns stream
        justRun { trackingService.trackCacheObject(cacheObject, "targetbucket") }

        // Act
        val result = underTest.getObjectStream(cacheObject, "testpath")

        // Assert
        assert(result == stream)
        assert(getObjectArgsSlot.isCaptured)
        assert(getObjectArgsSlot.captured.`object`() == "targetpath")
        assert(getObjectArgsSlot.captured.bucket() == "targetbucket")

        verifySequence {
            bucketStrategy.getBucket(cacheObject)
            bucketStrategy.getStoragePath(cacheObject, "testpath")
            client.getObject(capture(getObjectArgsSlot))
            trackingService.trackCacheObject(cacheObject, "targetbucket")
        }
    }

    @Test
    fun testGetObjectChunkStreamCallsClientWithProperArguments() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val getObjectArgsSlot = slot<GetObjectArgs>()
        val stream = mockk<GetObjectResponse>()

        every { bucketStrategy.getBucket(cacheObject) } returns "targetbucket"
        every { bucketStrategy.getStoragePath(cacheObject) } returns "targetpath"
        every { client.getObject(capture(getObjectArgsSlot)) } returns stream
        justRun { trackingService.trackCacheObject(cacheObject, "targetbucket") }

        // Act
        val result = underTest.getObjectChunkStream(cacheObject, 5, 10, false)

        // Assert
        assert(result == stream)
        assert(getObjectArgsSlot.isCaptured)
        assert(getObjectArgsSlot.captured.`object`() == "targetpath")
        assert(getObjectArgsSlot.captured.bucket() == "targetbucket")
        assert(getObjectArgsSlot.captured.offset() == 10L)
        assert(getObjectArgsSlot.captured.length() == 5L)

        verifySequence {
            bucketStrategy.getBucket(cacheObject)
            bucketStrategy.getStoragePath(cacheObject)
            client.getObject(capture(getObjectArgsSlot))
            trackingService.trackCacheObject(cacheObject, "targetbucket")
        }
    }

    @Test
    fun testGetObjectChunkStreamCallsClientWithProperArgumentsForTempObject() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "file-pdf",
            hash = "abc123",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = 1
        )

        val getObjectArgsSlot = slot<GetObjectArgs>()
        val stream = mockk<GetObjectResponse>()

        every { bucketStrategy.getExtensionFromMimeType("application/pdf") } returns ".pdf"
        every { client.getObject(capture(getObjectArgsSlot)) } returns stream

        // Act
        val result = underTest.getObjectChunkStream(cacheObject, 5, 10, true)

        assert(result == stream)
        assert(getObjectArgsSlot.isCaptured)
        assert(getObjectArgsSlot.captured.`object`() == "file-pdf/123/abc123.pdf")
        assert(getObjectArgsSlot.captured.bucket() == "temp")
        assert(getObjectArgsSlot.captured.offset() == 10L)
        assert(getObjectArgsSlot.captured.length() == 5L)

        verifySequence {
            bucketStrategy.getExtensionFromMimeType("application/pdf")
            client.getObject(capture(getObjectArgsSlot))
        }
    }

    @Test
    fun testGetObjectChunkStreamStaticCallsClientWithProperArguments() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val getObjectArgsSlot = slot<GetObjectArgs>()
        val stream = mockk<GetObjectResponse>()

        every { bucketStrategy.getBucket(cacheObject) } returns "targetbucket"
        every { bucketStrategy.getStoragePath(cacheObject, "inputpath") } returns "targetpath"
        every { client.getObject(capture(getObjectArgsSlot)) } returns stream
        justRun { trackingService.trackCacheObject(cacheObject, "targetbucket") }

        // Act
        val result = underTest.getObjectChunkStream(cacheObject, "inputpath", 5, 10)

        // Assert
        assert(stream == result)
        assert(getObjectArgsSlot.isCaptured)
        assert(getObjectArgsSlot.captured.bucket() == "targetbucket")
        assert(getObjectArgsSlot.captured.`object`() == "targetpath")
        assert(getObjectArgsSlot.captured.offset() == 5L)
        assert(getObjectArgsSlot.captured.length() == 10L)

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject, "inputpath")
            bucketStrategy.getBucket(cacheObject)
            client.getObject(capture(getObjectArgsSlot))
            trackingService.trackCacheObject(cacheObject, "targetbucket")
        }
    }

    @Test
    fun testPutTempFileCallsClientWithProperArgumentForObjectOfKnownSizeWithProvidedMimeType() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "file-pdf",
            hash = "abc123",
            mimeType = "application/pdf",
            size = 145,
            repoId = "repoId123",
            quality = 1
        )

        val stream = "abc123".toByteArray().inputStream()
        val bucketExistArgSlot = slot<BucketExistsArgs>()
        val putObjectArgSlot = slot<PutObjectArgs>()

        every { client.bucketExists(capture(bucketExistArgSlot)) } returns true
        every { bucketStrategy.getExtensionFromMimeType("application/pdf") } returns ".pdf"
        every { client.putObject(capture(putObjectArgSlot)) } returns mockk<ObjectWriteResponse>()

        // Act
        underTest.putTempFile(cacheObject, stream)

        // Assert
        assert(bucketExistArgSlot.isCaptured)
        assert(bucketExistArgSlot.captured.bucket() == "temp")

        val capturedObjectArgs = putObjectArgSlot.captured
        assert(capturedObjectArgs.bucket() == "temp")
        assert(capturedObjectArgs.stream().readAllBytes().toString(Charsets.UTF_8) == "abc123")
        assert(capturedObjectArgs.`object`() == "file-pdf/123/abc123.pdf")
        assert(capturedObjectArgs.contentType() == "application/pdf")
        assert(capturedObjectArgs.objectSize() == 145L)
        val writtenMetadata = capturedObjectArgs.userMetadata()
        assert(writtenMetadata.size() == 0)

        verifySequence {
            client.bucketExists(capture(bucketExistArgSlot))
            bucketStrategy.getExtensionFromMimeType("application/pdf")
            client.putObject(capture(putObjectArgSlot))
        }
    }

    @Test
    fun testPutTempFileCallsClientWithProperArgumentForObjectOfUnknownSizeWithoutProvidedMimeType() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = "123",
            type = "file-pdf",
            hash = "abc123",
            mimeType = "",
            size = -1,
            repoId = "repoId123",
            quality = 1
        )

        val stream = "abc123".toByteArray().inputStream()
        val bucketExistArgSlot = slot<BucketExistsArgs>()
        val putObjectArgSlot = slot<PutObjectArgs>()

        every { client.bucketExists(capture(bucketExistArgSlot)) } returns true
        every { bucketStrategy.getExtensionFromMimeType("") } returns ""
        every { client.putObject(capture(putObjectArgSlot)) } returns mockk<ObjectWriteResponse>()

        // Act
        underTest.putTempFile(cacheObject, stream)

        // Assert
        assert(bucketExistArgSlot.isCaptured)
        assert(bucketExistArgSlot.captured.bucket() == "temp")

        val capturedObjectArgs = putObjectArgSlot.captured
        assert(capturedObjectArgs.bucket() == "temp")
        assert(capturedObjectArgs.stream().readAllBytes().toString(Charsets.UTF_8) == "abc123")
        assert(capturedObjectArgs.`object`() == "file-pdf/123/abc123")
        assert(capturedObjectArgs.contentType() == MediaType.APPLICATION_OCTET_STREAM_VALUE)
        assert(capturedObjectArgs.objectSize() == -1L)
        val writtenMetadata = capturedObjectArgs.userMetadata()
        assert(writtenMetadata.size() == 0)

        verifySequence {
            client.bucketExists(capture(bucketExistArgSlot))
            bucketStrategy.getExtensionFromMimeType("")
            client.putObject(capture(putObjectArgSlot))
        }
    }

    @Test
    fun testGetFilePropertiesCallsServiceWithProperArgsAndReturnsExpectedValues() {
        // Arrange
        val cacheObject = mockk<CacheObject>()

        val statObjectSlot = slot<StatObjectArgs>()
        val clientResponse = mockk<StatObjectResponse>()

        every { clientResponse.size() } returns 123L
        every { clientResponse.contentType() } returns "application/pdf"

        every { bucketStrategy.getStoragePath(cacheObject) } returns "somepath"
        every { bucketStrategy.getBucket(cacheObject) } returns "somebucket"
        every { client.statObject(capture(statObjectSlot)) } returns clientResponse

        excludeRecords{
            clientResponse.size()
            clientResponse.contentType()
        }

        // Act
        val result = underTest.getFileProperties(cacheObject)

        // Assert
        assert(statObjectSlot.isCaptured)
        assert(statObjectSlot.captured.bucket() == "somebucket")
        assert(statObjectSlot.captured.`object`() == "somepath")

        assert(result.size == 123L)
        assert(result.mimeType == "application/pdf")

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject)
            bucketStrategy.getBucket(cacheObject)
            client.statObject(capture(statObjectSlot))
        }
    }

    @Test
    fun testGetFilePropertiesThrowsProperExceptionIfClientThrowsException() {
        // Arrange
        val cacheObject = mockk<CacheObject>()

        every { bucketStrategy.getStoragePath(cacheObject) } returns "somepath"
        every { bucketStrategy.getBucket(cacheObject) } returns "somebucket"
        every { client.statObject(any()) } throws Exception()

        // Act and Assert
        assertThrows<ResourceNotFoundException> { underTest.getFileProperties(cacheObject) }

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject)
            bucketStrategy.getBucket(cacheObject)
            client.statObject(any())
        }
    }

    @Test
    fun testGetFilePropertiesStaticCallsServiceWithProperArgsAndReturnsExpectedValues() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val path = "inputpath"

        val statObjectSlot = slot<StatObjectArgs>()
        val clientResponse = mockk<StatObjectResponse>()

        every { clientResponse.size() } returns 123L
        every { clientResponse.contentType() } returns "application/pdf"

        every { bucketStrategy.getStoragePath(cacheObject, path) } returns "somepath"
        every { bucketStrategy.getBucket(cacheObject) } returns "somebucket"
        every { client.statObject(capture(statObjectSlot)) } returns clientResponse

        excludeRecords{
            clientResponse.size()
            clientResponse.contentType()
        }

        // Act
        val result = underTest.getFileProperties(cacheObject, path)

        // Assert
        assert(statObjectSlot.isCaptured)
        assert(statObjectSlot.captured.bucket() == "somebucket")
        assert(statObjectSlot.captured.`object`() == "somepath")

        assert(result.size == 123L)
        assert(result.mimeType == "application/pdf")

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject, path)
            bucketStrategy.getBucket(cacheObject)
            client.statObject(capture(statObjectSlot))
        }
    }

    @Test
    fun testGetFilePropertiesStaticThrowsProperExceptionIfClientThrowsException() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val path = "inputpath"

        every { bucketStrategy.getStoragePath(cacheObject, path) } returns "somepath"
        every { bucketStrategy.getBucket(cacheObject) } returns "somebucket"
        every { client.statObject(any()) } throws Exception()

        // Act and Assert
        assertThrows<ResourceNotFoundException> { underTest.getFileProperties(cacheObject, path) }

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject, path)
            bucketStrategy.getBucket(cacheObject)
            client.statObject(any())
        }
    }

    @Test
    fun testObjectExistsReturnsTrueIfStatObjectFound() {
        // Arrange
        val cacheObject = mockk<CacheObject>()

        val statObjectSlot = slot<StatObjectArgs>()
        val clientResponse = mockk<StatObjectResponse>()

        every { bucketStrategy.getStoragePath(cacheObject) } returns "somepath"
        every { bucketStrategy.getBucket(cacheObject) } returns "somebucket"
        every { client.statObject(capture(statObjectSlot)) } returns clientResponse

        // Act
        val result = underTest.objectExists(cacheObject)

        // Assert
        assert(statObjectSlot.isCaptured)
        assert(statObjectSlot.captured.bucket() == "somebucket")
        assert(statObjectSlot.captured.`object`() == "somepath")

        assert(result == true)

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject)
            bucketStrategy.getBucket(cacheObject)
            client.statObject(capture(statObjectSlot))
        }
    }

    @Test
    fun testObjectExistsReturnsFalseIfStatObjectNotFound() {
        // Arrange
        val cacheObject = mockk<CacheObject>()

        val statObjectSlot = slot<StatObjectArgs>()

        every { bucketStrategy.getStoragePath(cacheObject) } returns "somepath"
        every { bucketStrategy.getBucket(cacheObject) } returns "somebucket"
        every { client.statObject(capture(statObjectSlot)) } throws Exception()

        // Act
        val result = underTest.objectExists(cacheObject)

        // Assert
        assert(statObjectSlot.isCaptured)
        assert(statObjectSlot.captured.bucket() == "somebucket")
        assert(statObjectSlot.captured.`object`() == "somepath")

        assert(result == false)

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject)
            bucketStrategy.getBucket(cacheObject)
            client.statObject(capture(statObjectSlot))
        }
    }

    @Test
    fun testObjectExistsStaticReturnsTrueIfStatObjectFound() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val path = "inputpath"

        val statObjectSlot = slot<StatObjectArgs>()
        val clientResponse = mockk<StatObjectResponse>()

        every { bucketStrategy.getStoragePath(cacheObject, path) } returns "somepath"
        every { bucketStrategy.getBucket(cacheObject) } returns "somebucket"
        every { client.statObject(capture(statObjectSlot)) } returns clientResponse

        // Act
        val result = underTest.objectExists(cacheObject, path)

        // Assert
        assert(statObjectSlot.isCaptured)
        assert(statObjectSlot.captured.bucket() == "somebucket")
        assert(statObjectSlot.captured.`object`() == "somepath")

        assert(result == true)

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject, path)
            bucketStrategy.getBucket(cacheObject)
            client.statObject(capture(statObjectSlot))
        }
    }

    @Test
    fun testObjectExistsStaticReturnsFalseIfStatObjectNotFound() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val path = "somepath"

        val statObjectSlot = slot<StatObjectArgs>()

        every { bucketStrategy.getStoragePath(cacheObject, path) } returns "somepath"
        every { bucketStrategy.getBucket(cacheObject) } returns "somebucket"
        every { client.statObject(capture(statObjectSlot)) } throws Exception()

        // Act
        val result = underTest.objectExists(cacheObject, path)

        // Assert
        assert(statObjectSlot.isCaptured)
        assert(statObjectSlot.captured.bucket() == "somebucket")
        assert(statObjectSlot.captured.`object`() == "somepath")

        assert(result == false)

        verifySequence {
            bucketStrategy.getStoragePath(cacheObject, path)
            bucketStrategy.getBucket(cacheObject)
            client.statObject(capture(statObjectSlot))
        }
    }

}
