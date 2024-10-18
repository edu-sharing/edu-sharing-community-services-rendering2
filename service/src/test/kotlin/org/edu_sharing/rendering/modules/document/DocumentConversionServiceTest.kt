package org.edu_sharing.rendering.modules.document

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verifySequence
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.modules.ConverterWebServiceArguments
import org.edu_sharing.rendering.modules.ConverterWebServiceCaller
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.document.DocumentReceiver.Companion.PUBLIC_FAILURE_MESSAGE
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.reactive.function.client.WebClient

@ExtendWith(MockKExtension::class)
class DocumentConversionServiceTest {

    private val dummyCacheObjectWord = CacheObject(
        nodeId = "test_node_id",
        hash = "test_hash",
        type = "doc",
        mimeType = "application/msword",
        repoId = "repo123"
    )

    private val dummyCacheObjectExcel = CacheObject(
        nodeId = "test_node_id",
        hash = "test_hash",
        type = "doc",
        mimeType = "application/vnd.ms-excel",
        repoId = "repo123"
    )

    private val dummyCacheObjectWithNonsenseMimeType = CacheObject(
        nodeId = "test_node_id",
        hash = "test_hash",
        type = "doc",
        mimeType = "text/octet-stream",
        repoId = "repo123"
    )

    // Mock objects
    private val documentConverterClient = mockk<WebClient>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val moduleRegistry = mockk<ModuleRegistry>()
    private val serviceCaller = mockk<ConverterWebServiceCaller>()
    private val spreadSheetRenderModule: SpreadsheetRenderModule = mockk()

    // Helper
    private val jobDataProvider = JobDataProvider()

    // Class under test
    private lateinit var underTest: DocumentConversionService

    @AfterEach
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun testProcessCallsConversionToPdfForWordFileIfSpreadsheetToHtmlIsActive() {
        underTest = DocumentConversionService(
            documentConverterWebClient = documentConverterClient,
            moduleRegistry = moduleRegistry,
            serviceCaller = serviceCaller,
            subJobRepository = subJobRepository,
            spreadsheetRenderModule = spreadSheetRenderModule
        )
        // Arrange
        val job = mockk<RenderingJob>()
        val subId = ObjectId().toString()
        val subJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.QUEUED,
            module = "DOCUMENT",
            mimeType = "application/msword"
        )
        val processingSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.PROCESSING,
            module = "DOCUMENT",
            mimeType = "application/msword"
        )
        val finishedSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.FINISHED,
            module = "DOCUMENT",
            mimeType = "application/msword"
        )
        val module = mockk<DocumentRenderModule>()
        val argumentSlot = slot<ConverterWebServiceArguments>()

        every { job.subJobs } returns mutableListOf(subJob)
        every { subJobRepository.save(processingSubJob) } returns mockk<SubJob>()
        every { subJobRepository.save(finishedSubJob) } returns mockk<SubJob>()
        every { job.module } returns "DOCUMENT"
        every { module.module() } returns "DOCUMENT"
        every { module.getTargetMimetype() } returns "application/pdf"
        every { spreadSheetRenderModule.module() } returns "SPREADSHEET"
        every { moduleRegistry.getRenderModule<RenderModule>("DOCUMENT") } returns module
        justRun { serviceCaller.callConverterService(capture(argumentSlot)) }

        excludeRecords {
            job.subJobs
            job.module
            module.module()
            module.getTargetMimetype()
            spreadSheetRenderModule.module()
        }

        // Act
        underTest.process(dummyCacheObjectWord, job)

        // Assert
        assert(argumentSlot.isCaptured)
        assert(argumentSlot.captured.client == documentConverterClient)
        assert(argumentSlot.captured.originalFileExtension == ".doc")
        assert(argumentSlot.captured.cacheObject == dummyCacheObjectWord)
        assert(argumentSlot.captured.externalServiceMethodPath == "conversion")
        assert(argumentSlot.captured.urlParams.isEmpty())

        verifySequence {
            subJobRepository.save(any())
            moduleRegistry.getRenderModule<RenderModule>("DOCUMENT")
            serviceCaller.callConverterService(capture(argumentSlot))
            subJobRepository.save(finishedSubJob)
        }
    }

    @Test
    fun testProcessCallsConversionToPdfForWordFileIfSpreadsheetToHtmlIsNotActive() {
        underTest = DocumentConversionService(
            documentConverterWebClient = documentConverterClient,
            moduleRegistry = moduleRegistry,
            serviceCaller = serviceCaller,
            subJobRepository = subJobRepository,
            spreadsheetRenderModule = null
        )
        // Arrange
        val job = mockk<RenderingJob>()
        val subId = ObjectId().toString()
        val subJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.QUEUED,
            module = "DOCUMENT",
            mimeType = "application/msword"
        )
        val processingSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.PROCESSING,
            module = "DOCUMENT",
            mimeType = "application/msword"
        )
        val finishedSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.FINISHED,
            module = "DOCUMENT",
            mimeType = "application/msword"
        )
        val module = mockk<DocumentRenderModule>()
        val argumentSlot = slot<ConverterWebServiceArguments>()

        every { job.subJobs } returns mutableListOf(subJob)
        every { subJobRepository.save(processingSubJob) } returns mockk<SubJob>()
        every { subJobRepository.save(finishedSubJob) } returns mockk<SubJob>()
        every { job.module } returns "DOCUMENT"
        every { module.module() } returns "DOCUMENT"
        every { module.getTargetMimetype() } returns "application/pdf"
        every { moduleRegistry.getRenderModule<RenderModule>("DOCUMENT") } returns module
        justRun { serviceCaller.callConverterService(capture(argumentSlot)) }

        excludeRecords {
            job.subJobs
            job.module
            module.module()
            module.getTargetMimetype()
        }

        // Act
        underTest.process(dummyCacheObjectWord, job)

        // Assert
        assert(argumentSlot.isCaptured)
        assert(argumentSlot.captured.client == documentConverterClient)
        assert(argumentSlot.captured.originalFileExtension == ".doc")
        assert(argumentSlot.captured.cacheObject == dummyCacheObjectWord)
        assert(argumentSlot.captured.externalServiceMethodPath == "conversion")
        assert(argumentSlot.captured.urlParams.isEmpty())

        verifySequence {
            subJobRepository.save(any())
            moduleRegistry.getRenderModule<RenderModule>("DOCUMENT")
            serviceCaller.callConverterService(capture(argumentSlot))
            subJobRepository.save(finishedSubJob)
        }
    }

    @Test
    fun testProcessCallsConversionToPdfForExcelFileIfSpreadsheetToHtmlIsNotActive() {
        underTest = DocumentConversionService(
            documentConverterWebClient = documentConverterClient,
            moduleRegistry = moduleRegistry,
            serviceCaller = serviceCaller,
            subJobRepository = subJobRepository,
            spreadsheetRenderModule = null
        )
        // Arrange
        val job = mockk<RenderingJob>()
        val subId = ObjectId().toString()
        val subJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.QUEUED,
            module = "DOCUMENT",
            mimeType = "application/vnd.ms-excel"
        )
        val processingSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.PROCESSING,
            module = "DOCUMENT",
            mimeType = "application/vnd.ms-excel"
        )
        val finishedSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.FINISHED,
            module = "DOCUMENT",
            mimeType = "application/vnd.ms-excel"
        )
        val module = mockk<DocumentRenderModule>()
        val argumentSlot = slot<ConverterWebServiceArguments>()

        every { job.subJobs } returns mutableListOf(subJob)
        every { subJobRepository.save(processingSubJob) } returns mockk<SubJob>()
        every { subJobRepository.save(finishedSubJob) } returns mockk<SubJob>()
        every { job.module } returns "DOCUMENT"
        every { module.module() } returns "DOCUMENT"
        every { module.getTargetMimetype() } returns "application/pdf"
        every { moduleRegistry.getRenderModule<RenderModule>("DOCUMENT") } returns module
        justRun { serviceCaller.callConverterService(capture(argumentSlot)) }

        excludeRecords {
            job.subJobs
            job.module
            module.module()
            module.getTargetMimetype()
        }

        // Act
        underTest.process(dummyCacheObjectExcel, job)

        // Assert
        assert(argumentSlot.isCaptured)
        assert(argumentSlot.captured.client == documentConverterClient)
        assert(argumentSlot.captured.originalFileExtension == ".xls")
        assert(argumentSlot.captured.cacheObject == dummyCacheObjectExcel)
        assert(argumentSlot.captured.externalServiceMethodPath == "conversion")
        assert(argumentSlot.captured.urlParams.isEmpty())

        verifySequence {
            subJobRepository.save(any())
            moduleRegistry.getRenderModule<RenderModule>("DOCUMENT")
            serviceCaller.callConverterService(capture(argumentSlot))
            subJobRepository.save(finishedSubJob)
        }
    }

    @Test
    fun testProcessCallsConversionToHtmlForExcelFileIfSpreadsheetToHtmlIsActive() {
        underTest = DocumentConversionService(
            documentConverterWebClient = documentConverterClient,
            moduleRegistry = moduleRegistry,
            serviceCaller = serviceCaller,
            subJobRepository = subJobRepository,
            spreadsheetRenderModule = spreadSheetRenderModule
        )
        // Arrange
        val job = mockk<RenderingJob>()
        val subId = ObjectId().toString()
        val subJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.QUEUED,
            module = "DOCUMENT",
            mimeType = "application/vnd.ms-excel"
        )
        val processingSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.PROCESSING,
            module = "DOCUMENT",
            mimeType = "application/vnd.ms-excel"
        )
        val finishedSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.FINISHED,
            module = "DOCUMENT",
            mimeType = "application/vnd.ms-excel"
        )
        val module = mockk<SpreadsheetRenderModule>()
        val argumentSlot = slot<ConverterWebServiceArguments>()

        every { job.subJobs } returns mutableListOf(subJob)
        every { subJobRepository.save(processingSubJob) } returns mockk<SubJob>()
        every { subJobRepository.save(finishedSubJob) } returns mockk<SubJob>()
        every { job.module } returns "SPREADSHEET"
        every { module.module() } returns "SPREADSHEET"
        every { module.getTargetMimetype() } returns "text/html"
        every { spreadSheetRenderModule.module() } returns "SPREADSHEET"
        every { moduleRegistry.getRenderModule<RenderModule>("SPREADSHEET") } returns module
        justRun { serviceCaller.callConverterService(capture(argumentSlot)) }

        excludeRecords {
            job.subJobs
            job.module
            module.module()
            module.getTargetMimetype()
            spreadSheetRenderModule.module()
        }

        // Act
        underTest.process(dummyCacheObjectExcel, job)

        // Assert
        assert(argumentSlot.isCaptured)
        assert(argumentSlot.captured.client == documentConverterClient)
        assert(argumentSlot.captured.originalFileExtension == ".xls")
        assert(argumentSlot.captured.cacheObject == dummyCacheObjectExcel)
        assert(argumentSlot.captured.externalServiceMethodPath == "conversion")
        assert(argumentSlot.captured.urlParams["format"] == "html")

        verifySequence {
            subJobRepository.save(any())
            moduleRegistry.getRenderModule<RenderModule>("SPREADSHEET")
            serviceCaller.callConverterService(capture(argumentSlot))
            subJobRepository.save(finishedSubJob)
        }
    }

    @Test
    fun testProcessCallsSetsSubJobToFailedForUnsupportedMimeType() {
        underTest = DocumentConversionService(
            documentConverterWebClient = documentConverterClient,
            moduleRegistry = moduleRegistry,
            serviceCaller = serviceCaller,
            subJobRepository = subJobRepository,
            spreadsheetRenderModule = spreadSheetRenderModule
        )
        // Arrange
        val job = mockk<RenderingJob>()
        val subId = ObjectId().toString()
        val subJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.QUEUED,
            module = "DOCUMENT",
            mimeType = "application/msword"
        )
        val processingSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.PROCESSING,
            module = "DOCUMENT",
            mimeType = "application/msword"
        )
        val failedSubJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = JobStatus.FAILED,
            module = "DOCUMENT",
            mimeType = "application/msword"
        )
        failedSubJob.message = PUBLIC_FAILURE_MESSAGE
        val module = mockk<DocumentRenderModule>()

        every { job.subJobs } returns mutableListOf(subJob)
        every { subJobRepository.save(processingSubJob) } returns mockk<SubJob>()
        every { subJobRepository.save(failedSubJob) } returns mockk<SubJob>()
        every { job.module } returns "DOCUMENT"
        every { job.esObjectId } returns "node123"
        every { module.module() } returns "DOCUMENT"
        every { module.getTargetMimetype() } returns "application/pdf"
        every { spreadSheetRenderModule.module() } returns "SPREADSHEET"
        every { moduleRegistry.getRenderModule<RenderModule>("DOCUMENT") } returns module

        excludeRecords {
            job.subJobs
            job.module
            job.esObjectId
            module.module()
            module.getTargetMimetype()
            spreadSheetRenderModule.module()
        }

        // Act
        underTest.process(dummyCacheObjectWithNonsenseMimeType, job)

        // Assert

        verifySequence {
            subJobRepository.save(any())
            moduleRegistry.getRenderModule<RenderModule>("DOCUMENT")
            subJobRepository.save(failedSubJob)
        }
    }
}
