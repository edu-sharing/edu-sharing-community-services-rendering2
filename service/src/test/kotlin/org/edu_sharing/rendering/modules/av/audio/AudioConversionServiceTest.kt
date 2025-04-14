package org.edu_sharing.rendering.modules.av.audio

import io.mockk.*
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.modules.av.AvConversionListener
import org.edu_sharing.rendering.modules.av.AvFileHelper
import org.edu_sharing.rendering.modules.av.audio.AudioConversionService.Companion.CODEC
import org.edu_sharing.rendering.modules.av.audio.AudioConversionService.Companion.OUTPUT_FORMAT
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.ObjectFactory
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.EncodingAttributes
import java.io.File

class AudioConversionServiceTest {
    companion object {
        const val DUMMY_ORIGINAL_FILE_PATH = "src/test/resources/fixtures/beep.wav"
    }

    private val listenerFactory: ObjectFactory<AvConversionListener> = mockk()
    private val encoder: Encoder = mockk()
    private val fileHelperFactory: ObjectFactory<AvFileHelper> = mockk()
    private val underTest = AudioConversionService(
        listenerFactory = listenerFactory,
        encoder = encoder,
        avFileHelperFactory = fileHelperFactory
    )
    private val jobDataProvider = JobDataProvider()

    @Test
    fun testConvertThrowsExceptionAndCallsCleanupIfEncoderThrowsException() {
        // Arrange
        underTest.bitrate = "160"
        val listener: AvConversionListener = mockk()
        val fileHelper: AvFileHelper = mockk()
        val cacheObject = getCacheObjectForTesting()
        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "audio/wav",
            module = "AUDIO",
            status = SubJobStatus.PROCESSING
        )
        val listenerSubJobSlot = slot<SubJob>()
        val dummyOutputFile = File("testFile")
        val dummyOriginalFile = File(DUMMY_ORIGINAL_FILE_PATH)
        every { listenerFactory.`object` } returns listener
        every { fileHelperFactory.`object` } returns fileHelper
        justRun { listener.subJob = capture(listenerSubJobSlot) }
        justRun { fileHelper.initOutputTempFile(OUTPUT_FORMAT) }
        justRun { fileHelper.fetchOriginalTempFile(cacheObject) }
        every { fileHelper.outputFile } returns dummyOutputFile
        every { fileHelper.originalFile } returns dummyOriginalFile

        val attrSlot = slot<EncodingAttributes>()
        val mmoSlot = slot<MultimediaObject>()
        every { encoder.encode(capture(mmoSlot), dummyOutputFile, capture(attrSlot), listener) } throws Exception()
        justRun { fileHelper.close() }

        // Act
        assertThrows<Exception> { underTest.convert(cacheObject, subJob) }

        // Assert
        assert(listenerSubJobSlot.captured.id == ObjectId(JobDataProvider.SUB_ID_1))
        assert(attrSlot.captured.audioAttributes.flatMap { it.bitRate }.get() == 160)
        assert(attrSlot.captured.audioAttributes.flatMap { it.codec }.get() == CODEC)
        assert(attrSlot.captured.audioAttributes.flatMap { it.channels }.get() == 2)
        assert(attrSlot.captured.audioAttributes.flatMap { it.codec }.get() == CODEC)
        assert(attrSlot.captured.outputFormat.get() == OUTPUT_FORMAT)
        assert(mmoSlot.captured.file.toString() == DUMMY_ORIGINAL_FILE_PATH)

        verifySequence {
            fileHelper.initOutputTempFile(OUTPUT_FORMAT)
            fileHelper.fetchOriginalTempFile(cacheObject)
            fileHelper.originalFile
            fileHelper.outputFile
            encoder.encode(any() as MultimediaObject, dummyOutputFile, any(), listener)
            fileHelper.close()
        }

        dummyOutputFile.delete()
    }

    @Test
    fun testConvertExecutesCorrectSequenceOnSuccessfulRun() {
        // Arrange
        underTest.bitrate = "160"
        val listener: AvConversionListener = mockk()
        val fileHelper: AvFileHelper = mockk()
        val cacheObject = getCacheObjectForTesting()
        val subJob = jobDataProvider.getDummySubJob(
            subId = JobDataProvider.SUB_ID_1,
            mimeType = "audio/wav",
            module = "AUDIO",
            status = SubJobStatus.PROCESSING
        )
        val dummyOutputFile = File("testFile")
        val dummyOriginalFile = File(DUMMY_ORIGINAL_FILE_PATH)
        every { listenerFactory.`object` } returns listener
        every { fileHelperFactory.`object` } returns fileHelper
        justRun { listener.subJob = any() }
        justRun { fileHelper.initOutputTempFile(OUTPUT_FORMAT) }
        justRun { fileHelper.fetchOriginalTempFile(cacheObject) }
        every { fileHelper.outputFile } returns dummyOutputFile
        every { fileHelper.originalFile } returns dummyOriginalFile
        justRun { encoder.encode(any() as MultimediaObject, dummyOutputFile, any(), listener) }
        val cacheObjectSlot = slot<CacheObject>()
        justRun { fileHelper.uploadToCache(capture(cacheObjectSlot)) }
        justRun { fileHelper.close() }

        // Act
        underTest.convert(cacheObject, subJob)

        // Assert
        verifySequence {
            fileHelper.initOutputTempFile(OUTPUT_FORMAT)
            fileHelper.fetchOriginalTempFile(cacheObject)
            fileHelper.originalFile
            fileHelper.outputFile
            encoder.encode(any() as MultimediaObject, dummyOutputFile, any(), listener)
            fileHelper.outputFile
            fileHelper.uploadToCache(any())
            fileHelper.close()
        }

        dummyOutputFile.delete()
    }

    private fun getCacheObjectForTesting(): CacheObject {
        return CacheObject(
            nodeId = "nodeId",
            hash = "hash",
            type = "file-audio",
            mimeType = "audio/wav",
            repoId = "repoIc"
        )
    }
}