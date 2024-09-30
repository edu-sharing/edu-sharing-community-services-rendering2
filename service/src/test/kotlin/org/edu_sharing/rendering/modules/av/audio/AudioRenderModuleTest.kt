package org.edu_sharing.rendering.modules.av.audio

/**
class AudioRenderModuleTest {
    private val mockAudioService = mockk<AudioService>()
    private val mockMapper = mockk<Mapper>()

    private val underTest = AudioRenderModule(45L, mockMapper, mockAudioService)

    private val cacheObject = CacheObject(
        nodeId = "nodeid",
        type = "audio",
        hash = "hash"
    )

    @Test
    fun testHandleReturnsLinksToCachedObjectsIfAlreadyCached() {
        // Arrange
        val mockRenderDataRequest = mockk<RenderDataRequest>()
        val linkList = listOf(ObjectLink(link = "mylink"))

        every { mockMapper.renderDataRequestToCacheObject(mockRenderDataRequest) } returns cacheObject
        every { mockAudioService.getObjectLinks(cacheObject) } returns linkList

        // Act
        val result = underTest.handle(mockRenderDataRequest)

        // Assert
        assert(result.module == RenderModules.AUDIO)
        assert(result.jobId == null)
        assert(result.objectLinks == linkList)

        verifySequence {
            mockMapper.renderDataRequestToCacheObject(mockRenderDataRequest)
            mockAudioService.getObjectLinks(cacheObject)
        }
    }

    @Test
    fun testHandleReturnsCreatedJobIdIfCreated() {
        // Arrange
        val mockRenderDataRequest = mockk<RenderDataRequest>()

        every { mockMapper.renderDataRequestToCacheObject(mockRenderDataRequest) } returns cacheObject
        every { mockAudioService.getObjectLinks(cacheObject) } returns null
        every { mockAudioService.retrieveOrCreateJob(cacheObject, RenderModules.AUDIO) } returns "newJob123"

        // Act
        val result = underTest.handle(mockRenderDataRequest)

        // Assert
        assert(result.module == RenderModules.AUDIO)
        assert(result.jobId == "newJob123")
        assert(result.objectLinks == null)

        verifySequence {
            mockMapper.renderDataRequestToCacheObject(mockRenderDataRequest)
            mockAudioService.getObjectLinks(cacheObject)
            mockAudioService.retrieveOrCreateJob(cacheObject, RenderModules.AUDIO)
        }
    }

    @Test
    fun testGetObjectLinkFromJobDataReturnsObjectLinkReturnedFromService() {
        // Arrange
        val linkList = listOf(ObjectLink(link = "mylink"))
        val mockRenderingJob = mockk<RenderingJob>()
        val subJobMock = mockk<SubJob>()

        every { mockMapper.renderingJobToCacheObject(mockRenderingJob) } returns cacheObject
        every { mockAudioService.getObjectLinks(cacheObject) } returns linkList

        // Act
        val result = underTest.getObjectLinkFromJobData(subJobMock, mockRenderingJob)

        // Assert
        assert(result?.link == "mylink")

        verifySequence {
            mockMapper.renderingJobToCacheObject(mockRenderingJob)
            mockAudioService.getObjectLinks(cacheObject)
        }
    }

    @Test
    fun testGetObjectLinkFromJobDataReturnsNullReturnedFromService() {
        // Arrange
        val mockRenderingJob = mockk<RenderingJob>()
        val subJobMock = mockk<SubJob>()

        every { mockMapper.renderingJobToCacheObject(mockRenderingJob) } returns cacheObject
        every { mockAudioService.getObjectLinks(cacheObject) } returns null

        // Act
        val result = underTest.getObjectLinkFromJobData(subJobMock, mockRenderingJob)

        // Assert
        assert(result == null)

        verifySequence {
            mockMapper.renderingJobToCacheObject(mockRenderingJob)
            mockAudioService.getObjectLinks(cacheObject)
        }
    }

    @Test
    fun testGetNodePermissionExpirationTimeReturnsProperTime() {
        assert(underTest.getNodePermissionExpirationTime() == 45L)
    }
}
        */