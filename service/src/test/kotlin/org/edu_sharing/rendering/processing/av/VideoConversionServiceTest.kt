package org.edu_sharing.rendering.processing.av

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifyOrder
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.processing.JobDataProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.ObjectFactory
import ws.schild.jave.Encoder

class VideoConversionServiceTest {
    private val listenerFactory: ObjectFactory<AVConversionListener> = mockk()
    private val encoder: Encoder = mockk()
    private val fileHelperFactory: ObjectFactory<AvFileHelper> = mockk()
    private val underTest = VideoConversionService(
        listenerFactory = listenerFactory,
        encoder = encoder,
        avFileHelperFactory = fileHelperFactory
    )
    private val jobDataProvider = JobDataProvider()

    @Test
    fun testConvertThrowsExceptionAndCallsCleanupIfFileHelperThrowsException() {
        underTest.videoFormat = "mp4"
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
        justRun { listener.subJob = any()  }
        justRun { fileHelper.initOutputTempFile("mp4")}
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

    private fun getCacheObjectForTesting(): CacheObject {
        return CacheObject(
            nodeId = "nodeId",
            hash = "hash",
            type = "file-audio",
            mimeType = "audio/wav",
        )
    }
}