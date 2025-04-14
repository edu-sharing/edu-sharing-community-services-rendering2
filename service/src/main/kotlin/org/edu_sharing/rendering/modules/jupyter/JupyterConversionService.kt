package org.edu_sharing.rendering.modules.jupyter

import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ErrorMessage
import org.edu_sharing.rendering.modules.ConversionService
import org.edu_sharing.rendering.modules.ConverterWebServiceArguments
import org.edu_sharing.rendering.modules.ConverterWebServiceCaller
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@ConditionalOnConverter
@Service
class JupyterConversionService(
    private val jupyterConverterWebClient: WebClient,
    private val module: JupyterRenderModule,
    private val subJobRepository: SubJobRepository,
    private val serviceCaller: ConverterWebServiceCaller
) : ConversionService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun process(
        cacheObject: CacheObject,
        renderingJob: RenderingJob
    ) {
        var subJob = renderingJob.subJobs.first()
        try {
            subJob.status = SubJobStatus.PROCESSING
            subJob = subJobRepository.save(subJob)
            convertAndMoveToCache(cacheObject)
            subJob.status = SubJobStatus.FINISHED
        } catch (exception: Exception) {
            log.error("Jupyter conversion failed for object ${renderingJob.esObjectId} with exception ${exception.message}", exception)
            subJob.status = SubJobStatus.FAILED
            subJob.errorMessage = ErrorMessage(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                message = exception.message,
                details = emptyMap(),
                exception = exception,
                userMessage = GENERIC_CONVERSION_ERROR
            )
        }
        subJobRepository.save(subJob)
    }

    private fun convertAndMoveToCache(cacheObject: CacheObject) {
        val arguments = ConverterWebServiceArguments(
            client = jupyterConverterWebClient,
            originalFileExtension = "ipynb",
            targetMimeType = module.getTargetMimetype(),
            externalServiceMethodPath = "convert",
            cacheObject = cacheObject
        )
        serviceCaller.callConverterService(arguments)
    }
}
