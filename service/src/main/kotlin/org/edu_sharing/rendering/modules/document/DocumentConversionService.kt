package org.edu_sharing.rendering.modules.document

import org.apache.tika.mime.MimeTypes
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.modules.ConversionService
import org.edu_sharing.rendering.modules.ConverterWebServiceArguments
import org.edu_sharing.rendering.modules.ConverterWebServiceCaller
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.document.DocumentReceiver.Companion.PUBLIC_FAILURE_MESSAGE
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@ConditionalOnConverter
@Service
class DocumentConversionService(
    private val documentConverterWebClient: WebClient,
    private val subJobRepository: SubJobRepository,
    private val moduleRegistry: ModuleRegistry,
    private val serviceCaller: ConverterWebServiceCaller,
    private val spreadsheetRenderModule: SpreadsheetRenderModule?
) : ConversionService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun process(cacheObject: CacheObject, renderingJob: RenderingJob) {
        var subJob = renderingJob.subJobs.first()
        subJob.status = JobStatus.PROCESSING
        subJob = subJobRepository.save(subJob)
        try {
            convertAndMoveToCache(
                cacheObject,
                moduleRegistry.getRenderModule(renderingJob.module)
            )
            subJob.status = JobStatus.FINISHED
        } catch (_: Exception) {
            log.error("Document conversion failed for object ${renderingJob.esObjectId}")
            subJob.status = JobStatus.FAILED
            subJob.message = PUBLIC_FAILURE_MESSAGE
        }
        subJobRepository.save(subJob)
    }

    private fun convertAndMoveToCache(cacheObject: CacheObject, module: DocumentRenderModule) {
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
