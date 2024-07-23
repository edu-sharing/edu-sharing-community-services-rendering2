package org.edu_sharing.rendering.service

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.AssetLinkParams
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AssetServiceTest {
    private val storageService = mockk<StorageService>()
    private val mapper = mockk<Mapper>()

    private lateinit var underTest: AssetService

    @BeforeEach
    fun setup() {
        underTest = AssetService(storageService, mapper)
        clearAllMocks()
    }

    @Test
    fun testGetAssetReturnsFullStreamIfNoRangeSet() {
        val assetParams = mockk<AssetLinkParams>()
        val cacheObject = mockk<CacheObject>()

        every { mapper.assetLinkParamsToCacheObject(assetParams) } returns cacheObject
        // Continue
    }
}