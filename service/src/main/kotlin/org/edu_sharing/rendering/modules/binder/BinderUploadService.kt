package org.edu_sharing.rendering.modules.binder

import org.edu_sharing.rendering.modules.binder.dto.BinderSseEvent
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate

@Service
class BinderUploadService(
    private val binderWebClient: WebClient,
    private val subJobRepository: SubJobRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private inline fun <reified T> typeReference() = object : ParameterizedTypeReference<ServerSentEvent<BinderSseEvent>>() {}

    fun triggerUpload(
        renderingJob: RenderingJob,
        gitHubUser: String,
        gitHubRepo: String,
    ) {
        if (renderingJob.subJobs.isEmpty()) {
            return
        }
        var subJob = renderingJob.subJobs.first()
        subJob.status = JobStatus.PROCESSING
        subJob.message = "Initializing binder import"
        subJob = subJobRepository.save(subJob)
        val eventStream = binderWebClient
            .get()
            .uri("/build/$gitHubUser/$gitHubRepo/HEAD")
            .retrieve()
            .bodyToFlux(typeReference<ServerSentEvent<BinderSseEvent>>())

        eventStream.subscribe({
            content ->
            {
                logger.info("Time: ${LocalDate.now()} - event: name[${content.event()}], id [${content.id()}], content[${content.data()}] ")
            }
        }, {
            error ->
            {
                logger.error("Error receiving SSE: $error")
            }
        }, {
            logger.info("Completed!")
        })

    }
}

