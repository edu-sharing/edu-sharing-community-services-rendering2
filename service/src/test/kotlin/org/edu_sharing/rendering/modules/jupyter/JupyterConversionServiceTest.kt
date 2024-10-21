package org.edu_sharing.rendering.modules.jupyter

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifySequence
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.modules.AbstractReceiver.Companion.PUBLIC_FAILURE_MESSAGE
import org.edu_sharing.rendering.modules.ConverterWebServiceArguments
import org.edu_sharing.rendering.modules.ConverterWebServiceCaller
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.reactive.function.client.WebClient

@ExtendWith(MockKExtension::class)
class JupyterConversionServiceTest {
    private val webClient = mockk<WebClient>()
    private val module = mockk<JupyterRenderModule>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val serviceCaller = mockk<ConverterWebServiceCaller>()
    private val jobDataProvider = JobDataProvider()

    private lateinit var underTest: JupyterConversionService

    @BeforeEach
    fun setup() {
        underTest = JupyterConversionService(
            jupyterConverterWebClient = webClient,
            module = module,
            subJobRepository = subJobRepository,
            serviceCaller = serviceCaller
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun testProcessCallsConvertAndMoveToCacheAndSetsSubJobToFinishedOnSuccess() {

        // Arrange
        val cacheObject = mockk<CacheObject>()
        val renderingJob = mockk<RenderingJob>()
        val subId = ObjectId().toString()
        val subJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.QUEUED,
            module = "JUPYTER",
            mimeType = "application/json"
        )

        val processingSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.PROCESSING,
            module = "JUPYTER",
            mimeType = "application/json"
        )

        val finishedSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.FINISHED,
            module = "JUPYTER",
            mimeType = "application/json"
        )

        every { renderingJob.subJobs } returns mutableListOf(subJob)
        every { module.getTargetMimetype() } returns "text/html"
        justRun {
            serviceCaller.callConverterService(
                ConverterWebServiceArguments(
                    client = webClient,
                    originalFileExtension = "ipynb",
                    targetMimeType = "text/html",
                    externalServiceMethodPath = "convert",
                    cacheObject = cacheObject
                )
            )
        }
        every { subJobRepository.save(processingSubJob) } returns processingSubJob
        every { subJobRepository.save(finishedSubJob) } returns finishedSubJob

        excludeRecords {
            renderingJob.subJobs
            module.getTargetMimetype()
        }

        // Act
        underTest.process(cacheObject, renderingJob)

        // Assert
        verifySequence {
            subJobRepository.save(any())
            serviceCaller.callConverterService(any())
            subJobRepository.save(any())
        }
    }

    @Test
    fun testProcessCallsConvertAndMoveToCacheAndSetsSubJobToFailedOnException() {
        // Arrange
        val cacheObject = mockk<CacheObject>()
        val renderingJob = mockk<RenderingJob>()
        val subId = ObjectId().toString()
        val subJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.PROCESSING,
            module = "JUPYTER",
            mimeType = "application/json"
        )

        val processingSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.PROCESSING,
            module = "JUPYTER",
            mimeType = "application/json"
        )

        val failedSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.FAILED,
            module = "JUPYTER",
            mimeType = "application/json"
        )
        failedSubJob.message = PUBLIC_FAILURE_MESSAGE

        every { renderingJob.subJobs } returns mutableListOf(subJob)
        every { renderingJob.esObjectId } returns "node123"
        every { module.getTargetMimetype() } returns "text/html"
        every {
            serviceCaller.callConverterService(
                ConverterWebServiceArguments(
                    client = webClient,
                    originalFileExtension = "ipynb",
                    targetMimeType = "text/html",
                    externalServiceMethodPath = "conversion",
                    cacheObject = cacheObject
                )
            )
        } throws Exception("testMessage")
        every { subJobRepository.save(processingSubJob) } returns processingSubJob
        every { subJobRepository.save(failedSubJob) } returns failedSubJob

        excludeRecords {
            renderingJob.subJobs
            module.getTargetMimetype()
        }

        // Act
        underTest.process(cacheObject, renderingJob)

        // Assert
        verifySequence {
            subJobRepository.save(any())
            serviceCaller.callConverterService(any())
            subJobRepository.save(any())
        }
    }
}