package org.edu_sharing.rendering.processing.av

/**
@ExtendWith(MockKExtension::class)
class VideoConversionServiceTest {
    private val listenerFactory = mockk<ObjectFactory<AVConversionListener>>()
    private val encoder = mockk<Encoder>()
    private val fileHelperFactory = mockk<ObjectFactory<AvFileHelper>>()
    private val jobDataProvider = JobDataProvider()

    private lateinit var underTest: VideoConversionService

    @BeforeEach
    fun setup() {
        underTest = VideoConversionService(
            listenerFactory = listenerFactory,
            encoder = encoder,
            avFileHelperFactory = fileHelperFactory
        )
        underTest.videoFormat = "mp4"
        underTest.videoResolutions = listOf(320, 720)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun testConvertThrowsExceptionAndCallsCleanupIfFileHelperThrowsException() {
        // Arrange
        val listener: AVConversionListener = mockk()
        val fileHelper: AvFileHelper = mockk()
        val cacheObject = getCacheObjectForTesting()
        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "audio/wav",
            module = RenderModules.AUDIO,
            status = JobStatus.PROCESSING
        )
        every { listenerFactory.`object` } returns listener
        every { fileHelperFactory.`object` } returns fileHelper
        justRun { listener.subJob = any() }
        justRun { fileHelper.initOutputTempFile("mp4") }
        every { fileHelper.fetchOriginalTempFile(cacheObject) } throws Exception()
        justRun { fileHelper.cleanup() }

        // Act
        assertThrows<Exception> { underTest.convert(cacheObject, subJob) }

        // Assert
        verifyOrder {
            listenerFactory.`object`
            fileHelperFactory.`object`
            listener.subJob = any()
            fileHelper.initOutputTempFile("mp4")
            fileHelper.fetchOriginalTempFile(cacheObject)
            fileHelper.cleanup()
        }
    }

    @Test
    fun testConvertCallsEncoderWithProperOptionsForInputData() {
        // Arrange
        val listener = mockk<AVConversionListener>()
        val avFileHelper = mockk<AvFileHelper>()

        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "video/mp4",
            module = RenderModules.VIDEO,
            status = JobStatus.QUEUED,
            quality = 320
        )
        val cacheObject = getCacheObjectForTesting()
        val outputFile = mockk<File>()
        val originalFile = File("src/test/resources/fixtures/testvideo480.mp4")

        val multimediaSlot = slot<MultimediaObject>()
        val attributeSlot = slot<EncodingAttributes>()

        val uploadObjectSlot = slot<CacheObject>()
        val uploadMetadataSlot = slot<Map<String, String>>()

        every { listenerFactory.`object` } returns listener
        every { fileHelperFactory.`object` } returns avFileHelper
        justRun { listener.subJob = subJob }
        justRun { avFileHelper.fetchOriginalTempFile(cacheObject)}
        every { outputFile.length() } returns 123
        every { listener.subJob } returns subJob
        justRun { avFileHelper.initOutputTempFile("mp4") }
        every { avFileHelper.originalFile } returns originalFile
        every { avFileHelper.outputFile } returns outputFile
        justRun { avFileHelper.uploadToCache(capture(uploadObjectSlot), capture(uploadMetadataSlot)) }
        justRun {
            encoder.encode(
                capture(multimediaSlot),
                outputFile,
                capture(attributeSlot),
                listener
            )
        }
        justRun { avFileHelper.cleanup() }

        excludeRecords {
            outputFile.length()
        }

        // Act
        underTest.convert(cacheObject, subJob)

        // Assert
        val multimediaCaptured = multimediaSlot.captured
        assert(multimediaCaptured.file == originalFile)

        val attributesCaptured = attributeSlot.captured
        val audioAttributes = attributesCaptured.audioAttributes.get()
        assert(audioAttributes.codec.get() == VideoConversionService.AUDIO_CODEC)
        assert(audioAttributes.bitRate.get() == VideoConversionService.AUDIO_BITRATE)
        val videoAttributes = attributesCaptured.videoAttributes.get()
        assert(videoAttributes.codec.get() == VideoConversionService.VIDEO_CODEC)
        assert(videoAttributes.crf.get() == VideoConversionService.VIDEO_CRF)
        val size = videoAttributes.size.get()
        assert(size.width == 480)
        assert(size.height == 320)

        val uploadObjectCaptured = uploadObjectSlot.captured
        assert(uploadObjectCaptured.quality == 320)
        assert(uploadObjectCaptured.size == 123L)
        assert(uploadObjectCaptured.mimeType == "video/mp4")

        val uploadMetadataCaptured = uploadMetadataSlot.captured
        assert(uploadMetadataCaptured.containsKey("isHighestResolution"))
        assert(uploadMetadataCaptured["isHighestResolution"] == "true")
        assert(uploadMetadataCaptured.containsKey("height"))
        assert(uploadMetadataCaptured["height"] == "320")
        assert(uploadMetadataCaptured.containsKey("width"))
        assert(uploadMetadataCaptured["width"] == "480")

        verifySequence {
            listenerFactory.`object`
            fileHelperFactory.`object`
            listener.subJob = subJob
            avFileHelper.initOutputTempFile("mp4")
            avFileHelper.fetchOriginalTempFile(cacheObject)
            avFileHelper.originalFile
            avFileHelper.outputFile
            encoder.encode(
                capture(multimediaSlot),
                outputFile,
                capture(attributeSlot),
                listener
            )
            avFileHelper.outputFile
            avFileHelper.uploadToCache(capture(uploadObjectSlot), capture(uploadMetadataSlot))
            avFileHelper.cleanup()
        }
    }

    @Test
    fun testConvertThrowsExceptionIfUpScalingDetected() {
        // Arrange
        val listener = mockk<AVConversionListener>()
        val avFileHelper = mockk<AvFileHelper>()

        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "video/mp4",
            module = RenderModules.VIDEO,
            status = JobStatus.QUEUED,
            quality = 720
        )
        val cacheObject = getCacheObjectForTesting()
        val originalFile = File("src/test/resources/fixtures/testvideo480.mp4")

        every { listenerFactory.`object` } returns listener
        every { fileHelperFactory.`object` } returns avFileHelper
        justRun { listener.subJob = subJob }
        justRun { avFileHelper.initOutputTempFile("mp4") }
        justRun { avFileHelper.fetchOriginalTempFile(cacheObject)}
        every { avFileHelper.originalFile } returns originalFile
        justRun { avFileHelper.cleanup() }

        assertThrows<ConversionException> { underTest.convert(cacheObject, subJob) }

        verifySequence {
            listenerFactory.`object`
            fileHelperFactory.`object`
            listener.subJob = subJob
            avFileHelper.initOutputTempFile("mp4")
            avFileHelper.fetchOriginalTempFile(cacheObject)
            avFileHelper.originalFile
            avFileHelper.cleanup()
        }
    }

    @Test
    fun testConvertThrowsExceptionIfVideoResolutionsAreNotSet() {
        // Arrange
        underTest.videoResolutions = emptyList()
        val listener = mockk<AVConversionListener>()
        val avFileHelper = mockk<AvFileHelper>()

        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "video/mp4",
            module = RenderModules.VIDEO,
            status = JobStatus.QUEUED,
            quality = 320
        )
        val cacheObject = getCacheObjectForTesting()
        val outputFile = mockk<File>()
        val originalFile = File("src/test/resources/fixtures/testvideo480.mp4")

        val multimediaSlot = slot<MultimediaObject>()
        val attributeSlot = slot<EncodingAttributes>()

        every { listenerFactory.`object` } returns listener
        every { fileHelperFactory.`object` } returns avFileHelper
        justRun { listener.subJob = subJob }
        justRun { avFileHelper.initOutputTempFile("mp4") }
        justRun { avFileHelper.fetchOriginalTempFile(cacheObject)}
        every { avFileHelper.originalFile } returns originalFile
        every { avFileHelper.outputFile } returns outputFile
        justRun {
            encoder.encode(
                capture(multimediaSlot),
                outputFile,
                capture(attributeSlot),
                listener
            )
        }
        justRun { avFileHelper.cleanup() }

        assertThrows<ConversionException> { underTest.convert(cacheObject, subJob) }

        verifySequence {
            listenerFactory.`object`
            fileHelperFactory.`object`
            listener.subJob = subJob
            avFileHelper.initOutputTempFile("mp4")
            avFileHelper.fetchOriginalTempFile(cacheObject)
            avFileHelper.originalFile
            avFileHelper.outputFile
            encoder.encode(
                capture(multimediaSlot),
                outputFile,
                capture(attributeSlot),
                listener
            )
            avFileHelper.cleanup()
        }
        underTest.videoResolutions = listOf(320, 720)

    }

    @Test
    fun testConvertCallsEncoderWithProperOptionsForInputDataAndResultingOddTargetWidth() {
        // Arrange
        underTest.videoResolutions = listOf(321, 723)
        val listener = mockk<AVConversionListener>()
        val avFileHelper = mockk<AvFileHelper>()

        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "video/mp4",
            module = RenderModules.VIDEO,
            status = JobStatus.QUEUED,
            quality = 321
        )
        val cacheObject = getCacheObjectForTesting()
        val outputFile = mockk<File>()
        val originalFile = File("src/test/resources/fixtures/testvideo480.mp4")

        val multimediaSlot = slot<MultimediaObject>()
        val attributeSlot = slot<EncodingAttributes>()

        val uploadObjectSlot = slot<CacheObject>()
        val uploadMetadataSlot = slot<Map<String, String>>()

        every { listenerFactory.`object` } returns listener
        every { fileHelperFactory.`object` } returns avFileHelper
        justRun { listener.subJob = subJob }
        justRun { avFileHelper.fetchOriginalTempFile(cacheObject)}
        every { outputFile.length() } returns 123
        every { listener.subJob } returns subJob
        justRun { avFileHelper.initOutputTempFile("mp4") }
        every { avFileHelper.originalFile } returns originalFile
        every { avFileHelper.outputFile } returns outputFile
        justRun { avFileHelper.uploadToCache(capture(uploadObjectSlot), capture(uploadMetadataSlot)) }
        justRun {
            encoder.encode(
                capture(multimediaSlot),
                outputFile,
                capture(attributeSlot),
                listener
            )
        }
        justRun { avFileHelper.cleanup() }

        excludeRecords {
            outputFile.length()
        }

        // Act
        underTest.convert(cacheObject, subJob)

        // Assert
        val multimediaCaptured = multimediaSlot.captured
        assert(multimediaCaptured.file == originalFile)

        val attributesCaptured = attributeSlot.captured
        val audioAttributes = attributesCaptured.audioAttributes.get()
        assert(audioAttributes.codec.get() == VideoConversionService.AUDIO_CODEC)
        assert(audioAttributes.bitRate.get() == VideoConversionService.AUDIO_BITRATE)
        val videoAttributes = attributesCaptured.videoAttributes.get()
        assert(videoAttributes.codec.get() == VideoConversionService.VIDEO_CODEC)
        assert(videoAttributes.crf.get() == VideoConversionService.VIDEO_CRF)
        val size = videoAttributes.size.get()
        assert(size.width == 480)
        assert(size.height == 321)

        val uploadObjectCaptured = uploadObjectSlot.captured
        assert(uploadObjectCaptured.quality == 321)
        assert(uploadObjectCaptured.size == 123L)
        assert(uploadObjectCaptured.mimeType == "video/mp4")

        val uploadMetadataCaptured = uploadMetadataSlot.captured
        assert(uploadMetadataCaptured.containsKey("isHighestResolution"))
        assert(uploadMetadataCaptured["isHighestResolution"] == "true")
        assert(uploadMetadataCaptured.containsKey("height"))
        assert(uploadMetadataCaptured["height"] == "321")
        assert(uploadMetadataCaptured.containsKey("width"))
        assert(uploadMetadataCaptured["width"] == "480")

        verifySequence {
            listenerFactory.`object`
            fileHelperFactory.`object`
            listener.subJob = subJob
            avFileHelper.initOutputTempFile("mp4")
            avFileHelper.fetchOriginalTempFile(cacheObject)
            avFileHelper.originalFile
            avFileHelper.outputFile
            encoder.encode(
                capture(multimediaSlot),
                outputFile,
                capture(attributeSlot),
                listener
            )
            avFileHelper.outputFile
            avFileHelper.uploadToCache(capture(uploadObjectSlot), capture(uploadMetadataSlot))
            avFileHelper.cleanup()
        }
        underTest.videoResolutions = listOf(320, 720)
    }

    @Test
    fun testConvertCallsEncoderWithProperOptionsForInputDataIfConvertedToMaxResolution() {
        // Arrange
        underTest.videoResolutions = listOf(320, 480)
        val listener = mockk<AVConversionListener>()
        val avFileHelper = mockk<AvFileHelper>()

        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "video/mp4",
            module = RenderModules.VIDEO,
            status = JobStatus.QUEUED,
            quality = 480
        )
        val cacheObject = getCacheObjectForTesting()
        val outputFile = mockk<File>()
        val originalFile = File("src/test/resources/fixtures/testvideo480.mp4")

        val multimediaSlot = slot<MultimediaObject>()
        val attributeSlot = slot<EncodingAttributes>()

        val uploadObjectSlot = slot<CacheObject>()
        val uploadMetadataSlot = slot<Map<String, String>>()

        every { listenerFactory.`object` } returns listener
        every { fileHelperFactory.`object` } returns avFileHelper
        justRun { listener.subJob = subJob }
        justRun { avFileHelper.fetchOriginalTempFile(cacheObject)}
        every { outputFile.length() } returns 123
        every { listener.subJob } returns subJob
        justRun { avFileHelper.initOutputTempFile("mp4") }
        every { avFileHelper.originalFile } returns originalFile
        every { avFileHelper.outputFile } returns outputFile
        justRun { avFileHelper.uploadToCache(capture(uploadObjectSlot), capture(uploadMetadataSlot)) }
        justRun {
            encoder.encode(
                capture(multimediaSlot),
                outputFile,
                capture(attributeSlot),
                listener
            )
        }
        justRun { avFileHelper.cleanup() }

        excludeRecords {
            outputFile.length()
        }

        // Act
        underTest.convert(cacheObject, subJob)

        // Assert
        val multimediaCaptured = multimediaSlot.captured
        assert(multimediaCaptured.file == originalFile)

        val attributesCaptured = attributeSlot.captured
        val audioAttributes = attributesCaptured.audioAttributes.get()
        assert(audioAttributes.codec.get() == VideoConversionService.AUDIO_CODEC)
        assert(audioAttributes.bitRate.get() == VideoConversionService.AUDIO_BITRATE)
        val videoAttributes = attributesCaptured.videoAttributes.get()
        assert(videoAttributes.codec.get() == VideoConversionService.VIDEO_CODEC)
        assert(videoAttributes.crf.get() == VideoConversionService.VIDEO_CRF)
        val size = videoAttributes.size.get()
        assert(size.width == 720)
        assert(size.height == 480)

        val uploadObjectCaptured = uploadObjectSlot.captured
        assert(uploadObjectCaptured.quality == 480)
        assert(uploadObjectCaptured.size == 123L)
        assert(uploadObjectCaptured.mimeType == "video/mp4")

        val uploadMetadataCaptured = uploadMetadataSlot.captured
        assert(uploadMetadataCaptured.containsKey("isHighestResolution"))
        assert(uploadMetadataCaptured["isHighestResolution"] == "true")
        assert(uploadMetadataCaptured.containsKey("height"))
        assert(uploadMetadataCaptured["height"] == "480")
        assert(uploadMetadataCaptured.containsKey("width"))
        assert(uploadMetadataCaptured["width"] == "720")

        verifySequence {
            listenerFactory.`object`
            fileHelperFactory.`object`
            listener.subJob = subJob
            avFileHelper.initOutputTempFile("mp4")
            avFileHelper.fetchOriginalTempFile(cacheObject)
            avFileHelper.originalFile
            avFileHelper.outputFile
            encoder.encode(
                capture(multimediaSlot),
                outputFile,
                capture(attributeSlot),
                listener
            )
            avFileHelper.outputFile
            avFileHelper.uploadToCache(capture(uploadObjectSlot), capture(uploadMetadataSlot))
            avFileHelper.cleanup()
        }
        underTest.videoResolutions = listOf(320, 720)
    }

    @Test
    fun testConvertCallsEncoderWithProperOptionsForInputDataIfHigherResolutionAvailable() {
        // Arrange
        underTest.videoResolutions = listOf(320, 480)
        val listener = mockk<AVConversionListener>()
        val avFileHelper = mockk<AvFileHelper>()

        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "video/mp4",
            module = RenderModules.VIDEO,
            status = JobStatus.QUEUED,
            quality = 320
        )
        val cacheObject = getCacheObjectForTesting()
        val outputFile = mockk<File>()
        val originalFile = File("src/test/resources/fixtures/testvideo480.mp4")

        val multimediaSlot = slot<MultimediaObject>()
        val attributeSlot = slot<EncodingAttributes>()

        val uploadObjectSlot = slot<CacheObject>()
        val uploadMetadataSlot = slot<Map<String, String>>()

        every { listenerFactory.`object` } returns listener
        every { fileHelperFactory.`object` } returns avFileHelper
        justRun { listener.subJob = subJob }
        justRun { avFileHelper.fetchOriginalTempFile(cacheObject)}
        every { outputFile.length() } returns 123
        every { listener.subJob } returns subJob
        justRun { avFileHelper.initOutputTempFile("mp4") }
        every { avFileHelper.originalFile } returns originalFile
        every { avFileHelper.outputFile } returns outputFile
        justRun { avFileHelper.uploadToCache(capture(uploadObjectSlot), capture(uploadMetadataSlot)) }
        justRun {
            encoder.encode(
                capture(multimediaSlot),
                outputFile,
                capture(attributeSlot),
                listener
            )
        }
        justRun { avFileHelper.cleanup() }

        excludeRecords {
            outputFile.length()
        }

        // Act
        underTest.convert(cacheObject, subJob)

        // Assert
        val multimediaCaptured = multimediaSlot.captured
        assert(multimediaCaptured.file == originalFile)

        val attributesCaptured = attributeSlot.captured
        val audioAttributes = attributesCaptured.audioAttributes.get()
        assert(audioAttributes.codec.get() == VideoConversionService.AUDIO_CODEC)
        assert(audioAttributes.bitRate.get() == VideoConversionService.AUDIO_BITRATE)
        val videoAttributes = attributesCaptured.videoAttributes.get()
        assert(videoAttributes.codec.get() == VideoConversionService.VIDEO_CODEC)
        assert(videoAttributes.crf.get() == VideoConversionService.VIDEO_CRF)
        val size = videoAttributes.size.get()
        assert(size.width == 480)
        assert(size.height == 320)

        val uploadObjectCaptured = uploadObjectSlot.captured
        assert(uploadObjectCaptured.quality == 320)
        assert(uploadObjectCaptured.size == 123L)
        assert(uploadObjectCaptured.mimeType == "video/mp4")

        val uploadMetadataCaptured = uploadMetadataSlot.captured
        assert(uploadMetadataCaptured.containsKey("isHighestResolution"))
        assert(uploadMetadataCaptured["isHighestResolution"] == "false")
        assert(uploadMetadataCaptured.containsKey("height"))
        assert(uploadMetadataCaptured["height"] == "320")
        assert(uploadMetadataCaptured.containsKey("width"))
        assert(uploadMetadataCaptured["width"] == "480")

        verifySequence {
            listenerFactory.`object`
            fileHelperFactory.`object`
            listener.subJob = subJob
            avFileHelper.initOutputTempFile("mp4")
            avFileHelper.fetchOriginalTempFile(cacheObject)
            avFileHelper.originalFile
            avFileHelper.outputFile
            encoder.encode(
                capture(multimediaSlot),
                outputFile,
                capture(attributeSlot),
                listener
            )
            avFileHelper.outputFile
            avFileHelper.uploadToCache(capture(uploadObjectSlot), capture(uploadMetadataSlot))
            avFileHelper.cleanup()
        }
        underTest.videoResolutions = listOf(320, 720)
    }



    private fun getCacheObjectForTesting(): CacheObject {
        return CacheObject(
            nodeId = "nodeId",
            hash = "hash",
            type = "file-video",
            mimeType = "video/mp4",
        )
    }
}
 */