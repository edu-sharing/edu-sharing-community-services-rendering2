package org.edu_sharing.rendering.modules.jupyter

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.modules.AbstractReceiver.Companion.PUBLIC_FAILURE_MESSAGE
import org.edu_sharing.rendering.modules.ConversionService
import org.edu_sharing.rendering.modules.ConverterWebServiceArguments
import org.edu_sharing.rendering.modules.ConverterWebServiceCaller
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
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
        val subJob = renderingJob.subJobs.first()
        try {
            convertAndMoveToCache(cacheObject)
            subJob.status = JobStatus.FINISHED
        } catch (e: Exception) {
            log.error("Jupyter conversion failed for object ${renderingJob.esObjectId} with exception ${e.message}")
            subJob.status = JobStatus.FAILED
            subJob.message = PUBLIC_FAILURE_MESSAGE
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