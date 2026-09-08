package org.edu_sharing.rendering.modules.document

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.modules.ConverterWebServiceArguments
import org.edu_sharing.rendering.modules.ConverterWebServiceCaller
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.storage.StorageService
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
    private val storageService = mockk<StorageService>()

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
            spreadsheetRenderModule = spreadSheetRenderModule,
            storageService = storageService
        )
        // Arrange
        val job = mockk<RenderingJob>()
        val subId = ObjectId().toString()
        val subJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = SubJobStatus.QUEUED,
            module = "DOCUMENT",
            mimeType = "application/msword"
        )
        val module = mockk<DocumentRenderModule>()
        val argumentSlot = slot<ConverterWebServiceArguments>()

        every { job.subJobs } returns mutableListOf(subJob)
        // Returns a copy (not the same reference) so later mutations of the returned subJob don't
        // retroactively change what this call was recorded with - production now also stamps
        // processingStartedDate/finishedDate with Instant.now(), which an eq()-matched stub can't predict.
        every { subJobRepository.save(any()) } answers { firstArg<SubJob>().copy() }
        every { job.module } returns "DOCUMENT"
        every { module.module() } returns "DOCUMENT"
        every { module.getTargetMimetype() } returns "application/pdf"
        every { spreadSheetRenderModule.module() } returns "SPREADSHEET"
        every { moduleRegistry.getRenderModule<RenderModule>("DOCUMENT") } returns module
        justRun { serviceCaller.callConverterService(capture(argumentSlot)) }
        justRun { storageService.removeTempObject(cacheObject = dummyCacheObjectWord) }

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
            subJobRepository.save(match { it.status == SubJobStatus.PROCESSING })
            moduleRegistry.getRenderModule<RenderModule>("DOCUMENT")
            serviceCaller.callConverterService(capture(argumentSlot))
            storageService.removeTempObject(cacheObject = dummyCacheObjectWord)
            subJobRepository.save(match { it.status == SubJobStatus.FINISHED })
        }
    }

    @Test
    fun testProcessCallsConversionToPdfForWordFileIfSpreadsheetToHtmlIsNotActive() {
        underTest = DocumentConversionService(
            documentConverterWebClient = documentConverterClient,
            moduleRegistry = moduleRegistry,
            serviceCaller = serviceCaller,
            subJobRepository = subJobRepository,
            spreadsheetRenderModule = null,
            storageService = storageService
        )
        // Arrange
        val job = mockk<RenderingJob>()
        val subId = ObjectId().toString()
        val subJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = SubJobStatus.QUEUED,
            module = "DOCUMENT",
            mimeType = "application/msword"
        )
        val module = mockk<DocumentRenderModule>()
        val argumentSlot = slot<ConverterWebServiceArguments>()

        every { job.subJobs } returns mutableListOf(subJob)
        every { subJobRepository.save(any()) } answers { firstArg<SubJob>().copy() }
        every { job.module } returns "DOCUMENT"
        every { module.module() } returns "DOCUMENT"
        every { module.getTargetMimetype() } returns "application/pdf"
        every { moduleRegistry.getRenderModule<RenderModule>("DOCUMENT") } returns module
        justRun { serviceCaller.callConverterService(capture(argumentSlot)) }
        justRun { storageService.removeTempObject(cacheObject = dummyCacheObjectWord) }

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
            subJobRepository.save(match { it.status == SubJobStatus.PROCESSING })
            moduleRegistry.getRenderModule<RenderModule>("DOCUMENT")
            serviceCaller.callConverterService(capture(argumentSlot))
            storageService.removeTempObject(cacheObject = dummyCacheObjectWord)
            subJobRepository.save(match { it.status == SubJobStatus.FINISHED })
        }
    }

    @Test
    fun testProcessCallsConversionToPdfForExcelFileIfSpreadsheetToHtmlIsNotActive() {
        underTest = DocumentConversionService(
            documentConverterWebClient = documentConverterClient,
            moduleRegistry = moduleRegistry,
            serviceCaller = serviceCaller,
            subJobRepository = subJobRepository,
            spreadsheetRenderModule = null,
            storageService = storageService
        )
        // Arrange
        val job = mockk<RenderingJob>()
        val subId = ObjectId().toString()
        val subJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = SubJobStatus.QUEUED,
            module = "DOCUMENT",
            mimeType = "application/vnd.ms-excel"
        )
        val module = mockk<DocumentRenderModule>()
        val argumentSlot = slot<ConverterWebServiceArguments>()

        every { job.subJobs } returns mutableListOf(subJob)
        every { subJobRepository.save(any()) } answers { firstArg<SubJob>().copy() }
        every { job.module } returns "DOCUMENT"
        every { module.module() } returns "DOCUMENT"
        every { module.getTargetMimetype() } returns "application/pdf"
        every { moduleRegistry.getRenderModule<RenderModule>("DOCUMENT") } returns module
        justRun { serviceCaller.callConverterService(capture(argumentSlot)) }
        justRun { storageService.removeTempObject(cacheObject = dummyCacheObjectExcel) }

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
            subJobRepository.save(match { it.status == SubJobStatus.PROCESSING })
            moduleRegistry.getRenderModule<RenderModule>("DOCUMENT")
            serviceCaller.callConverterService(capture(argumentSlot))
            storageService.removeTempObject(cacheObject = dummyCacheObjectExcel)
            subJobRepository.save(match { it.status == SubJobStatus.FINISHED })
        }
    }

    @Test
    fun testProcessCallsConversionToHtmlForExcelFileIfSpreadsheetToHtmlIsActive() {
        underTest = DocumentConversionService(
            documentConverterWebClient = documentConverterClient,
            moduleRegistry = moduleRegistry,
            serviceCaller = serviceCaller,
            subJobRepository = subJobRepository,
            spreadsheetRenderModule = spreadSheetRenderModule,
            storageService = storageService
        )
        // Arrange
        val job = mockk<RenderingJob>()
        val subId = ObjectId().toString()
        val subJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = SubJobStatus.QUEUED,
            module = "DOCUMENT",
            mimeType = "application/vnd.ms-excel"
        )
        val module = mockk<SpreadsheetRenderModule>()
        val argumentSlot = slot<ConverterWebServiceArguments>()

        every { job.subJobs } returns mutableListOf(subJob)
        every { subJobRepository.save(any()) } answers { firstArg<SubJob>().copy() }
        every { job.module } returns "SPREADSHEET"
        every { module.module() } returns "SPREADSHEET"
        every { module.getTargetMimetype() } returns "text/html"
        every { spreadSheetRenderModule.module() } returns "SPREADSHEET"
        every { moduleRegistry.getRenderModule<RenderModule>("SPREADSHEET") } returns module
        justRun { serviceCaller.callConverterService(capture(argumentSlot)) }
        justRun { storageService.removeTempObject(cacheObject = dummyCacheObjectExcel) }

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
            subJobRepository.save(match { it.status == SubJobStatus.PROCESSING })
            moduleRegistry.getRenderModule<RenderModule>("SPREADSHEET")
            serviceCaller.callConverterService(capture(argumentSlot))
            storageService.removeTempObject(cacheObject = dummyCacheObjectExcel)
            subJobRepository.save(match { it.status == SubJobStatus.FINISHED })
        }
    }

    @Test
    fun testProcessCallsSetsSubJobToFailedForUnsupportedMimeType() {
        underTest = DocumentConversionService(
            documentConverterWebClient = documentConverterClient,
            moduleRegistry = moduleRegistry,
            serviceCaller = serviceCaller,
            subJobRepository = subJobRepository,
            spreadsheetRenderModule = spreadSheetRenderModule,
            storageService = storageService
        )
        // Arrange
        val job = mockk<RenderingJob>()
        val subId = ObjectId().toString()
        val subJob = jobDataProvider.getDummySubJob(
            subId = subId,
            status = SubJobStatus.QUEUED,
            module = "DOCUMENT",
            mimeType = "application/msword"
        )
        val module = mockk<DocumentRenderModule>()

        every { job.subJobs } returns mutableListOf(subJob)
        every { subJobRepository.save(any()) } answers { firstArg<SubJob>().copy() }
        every { job.module } returns "DOCUMENT"
        every { job.esObjectId } returns "node123"
        every { module.module() } returns "DOCUMENT"
        every { module.getTargetMimetype() } returns "application/pdf"
        every { spreadSheetRenderModule.module() } returns "SPREADSHEET"
        every { moduleRegistry.getRenderModule<RenderModule>("DOCUMENT") } returns module
        every { storageService.removeTempObject(cacheObject = dummyCacheObjectWithNonsenseMimeType) } returns Unit

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
            subJobRepository.save(match { it.status == SubJobStatus.PROCESSING })
            moduleRegistry.getRenderModule<RenderModule>("DOCUMENT")
            storageService.removeTempObject(cacheObject = dummyCacheObjectWithNonsenseMimeType)
            subJobRepository.save(match { it.status == SubJobStatus.FAILED && it.errorMessage == GENERIC_CONVERSION_ERROR })
        }
    }
}
