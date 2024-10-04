package org.edu_sharing.rendering.modules.jupyter

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.modules.ConversionService
import org.edu_sharing.rendering.modules.document.DocumentReceiver.Companion.PUBLIC_FAILURE_MESSAGE
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import java.io.File
import java.nio.file.Files
import kotlin.io.inputStream

@ConditionalOnConverter
@Service
class JupyterConversionService(
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StorageService,
    private val jupyterConverterWebClient: WebClient,
    private val module: JupyterRenderModule,
    private val subJobRepository: SubJobRepository,
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
            log.error("Document conversion failed for object ${renderingJob.esObjectId} with exception ${e.message}")
            subJob.status = JobStatus.FAILED
            subJob.message = PUBLIC_FAILURE_MESSAGE
        }
        subJobRepository.save(subJob)
    }

    private fun convertAndMoveToCache(cacheObject: CacheObject) {
        val inputStream = contentTransferService.getAsInputStream(cacheObject)
        val originalFile = File(
            "${cacheObject.nodeId.substringBefore(".")}_${cacheObject.hash}.ipynb}"
        )
        inputStream.use {
            Files.copy(inputStream, originalFile.toPath())
        }
        val builder = MultipartBodyBuilder()
        builder.part("file", FileSystemResource(originalFile))
        try {
            val returnedData = jupyterConverterWebClient.post()
                .uri {
                    UriComponentsBuilder.fromUri(it.build()).path("/convert").build(true).toUri()
                }.contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(ByteArray::class.java)
                .block()
            val convertedCacheObject = cacheObject.copy()
            convertedCacheObject.mimeType = module.getTargetMimetype()
            if (returnedData == null) {
                throw Exception("Empty data returned")
            }
            val resultInputStream = returnedData.inputStream()
            resultInputStream.use {
                storageImplementation.putObject(convertedCacheObject, resultInputStream)
            }
        } catch (exception: Exception) {
            throw exception
        } finally {
            originalFile.delete()
        }
    }
}