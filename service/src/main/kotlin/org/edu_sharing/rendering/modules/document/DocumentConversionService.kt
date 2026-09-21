package org.edu_sharing.rendering.modules.document

import org.apache.tika.mime.MimeTypes
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.modules.ConversionService
import org.edu_sharing.rendering.modules.ConverterWebServiceArguments
import org.edu_sharing.rendering.modules.ConverterWebServiceCaller
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.renderingJob.SubJobHeartbeat
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant

@ConditionalOnConverter
@Service
class DocumentConversionService(
    private val documentConverterWebClient: WebClient,
    private val subJobRepository: SubJobRepository,
    private val moduleRegistry: ModuleRegistry,
    private val serviceCaller: ConverterWebServiceCaller,
    private val spreadsheetRenderModule: SpreadsheetRenderModule?,
    private val storageService: StorageService,
    private val subJobHeartbeat: SubJobHeartbeat
) : ConversionService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun process(cacheObject: CacheObject, renderingJob: RenderingJob) {
        log.debug("process: nodeId=${cacheObject.nodeId}, module=${renderingJob.module}")
        var subJob = renderingJob.subJobs.first()
        subJob.status = SubJobStatus.PROCESSING
        subJob.processingStartedDate = Instant.now()
        subJob = subJobRepository.save(subJob)
        try {
            // The converter call can legitimately run up to the long-running WebClient budget (10 min
            // default), well inside the reaper's PT30M default but a heartbeat is what keeps that true
            // if either value ever changes.
            subJobHeartbeat.run(subJob.id) {
                convertAndMoveToCache(
                    cacheObject,
                    moduleRegistry.getRenderModule(renderingJob.module)
                )
            }
            subJob.status = SubJobStatus.FINISHED
            subJob.finishedDate = Instant.now()
            log.debug("Document conversion FINISHED: nodeId=${cacheObject.nodeId}")
        } catch (exception: Exception) {
            log.error("Document conversion failed for object ${renderingJob.esObjectId} with exception: ${exception.message}",exception)
            subJob.status = SubJobStatus.FAILED
            subJob.finishedDate = Instant.now()
            subJob.errorMessage = GENERIC_CONVERSION_ERROR
        } finally {
            storageService.removeTempObject(cacheObject = cacheObject)
        }
        subJobRepository.save(subJob)
    }

    private fun convertAndMoveToCache(cacheObject: CacheObject, module: DocumentRenderModule) {
        log.debug("convertAndMoveToCache: nodeId=${cacheObject.nodeId}, mimeType=${cacheObject.mimeType}, targetMimeType=${module.getTargetMimetype()}, module=${module.module()}")
        val urlParams =
            if (spreadsheetRenderModule != null && module.module() == spreadsheetRenderModule.module()) mapOf("format" to "html") else emptyMap()
        val arguments = ConverterWebServiceArguments(
            client = documentConverterWebClient,
            originalFileExtension = getExtensionFromMimeType(cacheObject.mimeType),
            targetMimeType = module.getTargetMimetype(),
            cacheObject = cacheObject,
            externalServiceMethodPath = "conversion",
            urlParams = urlParams
        )
        serviceCaller.callConverterService(arguments = arguments)
    }

    private fun getExtensionFromMimeType(mimeType: String): String {
        val extension = MimeTypes.getDefaultMimeTypes().forName(mimeType).extension
        if (extension.isBlank()) {
            throw Exception("Mime type $mimeType is not supported")
        }
        return extension
    }
}
