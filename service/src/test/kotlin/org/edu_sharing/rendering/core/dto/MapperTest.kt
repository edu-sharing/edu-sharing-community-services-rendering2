package org.edu_sharing.rendering.core.dto

import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.asset.dto.AssetLinkParams
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class MapperTest {
    private val underTest = Mapper()

    private val nodeId = "node123"
    private val size = 450L
    private val type = "file-type"
    private val hash = "hash123"
    private val mimeType = "mime/type"
    private val version = "1.2"
    private val repoId = "repo123"
    private val title = "title123"
    private val url = "url123"

    @Test
    fun testRenderDataRequestToCacheObjectProperlyMaps() {
        // Arrange
        val request = RenderDataRequest(
            nodeId = nodeId,
            size = size,
            type = type,
            hash = hash,
            mimeType = mimeType,
            version = version,
            repoId = repoId,
            title = title,
            url = url,
            userData = RequestUserData(
                authorityName = "authority123",
                firstName = "Max",
                surName = "Mustermann",
                userEMail = "mail@mail.de"
            )
        )

        // Act
        val result = underTest.renderDataRequestToCacheObject(request)

        // Assert
        assert(result.nodeId == nodeId)
        assert(result.size == size)
        assert(result.type == type)
        assert(result.hash == hash)
        assert(result.mimeType == mimeType)
        assert(result.version == version)
        assert(result.repoId == repoId)
        assert(result.quality == null)
    }

    @Test
    fun testCacheObjectToRenderingJobProperlyMapsNonConversionObjectWithVersion() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = nodeId,
            type = type,
            hash = hash,
            mimeType = mimeType,
            repoId = repoId,
            version = version,
            size = size
        )

        // Act
        val result = underTest.cacheObjectToRenderingJob(cacheObject, "module123")

        // Assert
        assert(result.esObjectType == type)
        assert(result.esObjectId == nodeId)
        assert(result.esHash == hash)
        assert(result.mimeType == mimeType)
        assert(result.nodeVersion == version)
        assert(result.repoId == repoId)
        assert(result.size == size)
        assert(result.module == "module123")
        assert(result.conversionType == false)
    }

    @Test
    fun testCacheObjectToRenderingJobProperlyMapsConversionObjectWithoutVersion() {
        // Arrange
        val cacheObject = CacheObject(
            nodeId = nodeId,
            type = type,
            hash = hash,
            mimeType = mimeType,
            repoId = repoId,
            size = size
        )

        // Act
        val result = underTest.cacheObjectToRenderingJob(cacheObject, "module123", true)

        // Assert
        assert(result.esObjectType == type)
        assert(result.esObjectId == nodeId)
        assert(result.esHash == hash)
        assert(result.mimeType == mimeType)
        assert(result.nodeVersion.isBlank())
        assert(result.repoId == repoId)
        assert(result.size == size)
        assert(result.module == "module123")
        assert(result.conversionType == true)
    }

    @Test
    fun testRenderDataRequestToRenderingJobProperlyMapsRequest() {
        // Arrange
        val request = RenderDataRequest(
            nodeId = nodeId,
            size = size,
            type = type,
            hash = hash,
            mimeType = mimeType,
            version = version,
            repoId = repoId,
            title = title,
            url = url,
            userData = RequestUserData(
                authorityName = "authority123",
                firstName = "Max",
                surName = "Mustermann",
                userEMail = "mail@mail.de"
            )
        )

        // Act
        val result = underTest.renderDataRequestToRenderingJob(request, "module123")

        // Assert
        assert(result.esObjectType == type)
        assert(result.esObjectId == nodeId)
        assert(result.esHash == hash)
        assert(result.mimeType == mimeType)
        assert(result.nodeVersion == version)
        assert(result.repoId == repoId)
        assert(result.size == size)
        assert(result.conversionType == false)
    }

    @Test
    fun testRenderingJobToCacheObjectProperlyMapsJobWithSize() {
        // Arrange
        val job = RenderingJob(
            esObjectId = nodeId,
            esObjectType = type,
            esHash = hash,
            mimeType = mimeType,
            size = size,
            nodeVersion = version,
            repoId = repoId,
            module = "module123",
        )

        // Act
        val result = underTest.renderingJobToCacheObject(job)

        // Assert
        // Assert
        assert(result.nodeId == nodeId)
        assert(result.size == size)
        assert(result.type == type)
        assert(result.hash == hash)
        assert(result.mimeType == mimeType)
        assert(result.version == version)
        assert(result.repoId == repoId)
        assert(result.quality == null)
    }

    @Test
    fun testRenderingJobToCacheObjectProperlyMapsJobWithoutSize() {
        // Arrange
        val job = RenderingJob(
            esObjectId = nodeId,
            esObjectType = type,
            esHash = hash,
            mimeType = mimeType,
            size = null,
            nodeVersion = version,
            repoId = repoId,
            module = "module123",
        )

        // Act
        val result = underTest.renderingJobToCacheObject(job)

        // Assert
        assert(result.nodeId == nodeId)
        assert(result.size == -1L)
        assert(result.type == type)
        assert(result.hash == hash)
        assert(result.mimeType == mimeType)
        assert(result.version == version)
        assert(result.repoId == repoId)
        assert(result.quality == null)
    }

    @Test
    fun testAssetLinkParamsToCacheObjectProperlyMapsParamsWithNonZeroQuality() {
        // Arrange
        val assetLinkParams = AssetLinkParams(
            repoId = repoId,
            nodeId = nodeId,
            hash = hash,
            quality = 2,
            type = type,
            mimeType = mimeType
        )

        // Act
        val result = underTest.assetLinkParamsToCacheObject(assetLinkParams)

        // Assert
        assert(result.nodeId == nodeId)
        assert(result.size == -1L)
        assert(result.type == type)
        assert(result.hash == hash)
        assert(result.mimeType == mimeType)
        assert(result.version == null)
        assert(result.repoId == repoId)
        assert(result.quality == 2)
    }

    @Test
    fun testAssetLinkParamsToCacheObjectProperlyMapsParamsWithZeroQuality() {
        // Arrange
        val assetLinkParams = AssetLinkParams(
            repoId = repoId,
            nodeId = nodeId,
            hash = hash,
            quality = 0,
            type = type,
            mimeType = mimeType
        )

        // Act
        val result = underTest.assetLinkParamsToCacheObject(assetLinkParams)

        // Assert
        assert(result.nodeId == nodeId)
        assert(result.size == -1L)
        assert(result.type == type)
        assert(result.hash == hash)
        assert(result.mimeType == mimeType)
        assert(result.version == null)
        assert(result.repoId == repoId)
        assert(result.quality == null)
    }



}