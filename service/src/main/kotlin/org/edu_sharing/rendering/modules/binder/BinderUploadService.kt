package org.edu_sharing.rendering.modules.binder

import org.edu_sharing.rendering.modules.binder.dto.BinderPhases
import org.edu_sharing.rendering.modules.binder.dto.BinderSseEvent
import org.edu_sharing.rendering.modules.binder.dto.ProgressEntry
import org.edu_sharing.rendering.modules.binder.dto.ProgressInfoObject
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
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
    private val subJobRepository: SubJobRepository,
    private val mainJobLogic: MainJobLogic
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
                if (content.data() != null) {
                    updateSubJob(content.data() ?: BinderSseEvent(phase = "", message = ""), subJob)
                }
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

    private fun updateSubJob(eventData: BinderSseEvent, subJob: SubJob) {
        var hasBeenFinished = false
        when(eventData.phase) {
            BinderPhases.WAITING.event -> { subJob.progress = 5 }
            BinderPhases.FETCHING.event -> { subJob.progress = 10 }
            BinderPhases.BUILDING.event -> { subJob.progress = 15 }
            BinderPhases.PUSHING.event -> {
                if (eventData.progress != null) {
                    subJob.progress = 15 + calculatePushingProgress(eventData.progress)
                }
            }
            BinderPhases.BUILT.event -> { subJob.progress = 75 }
            BinderPhases.LAUNCHING.event -> { subJob.progress = 90 }
            BinderPhases.READY.event -> {
                subJob.progress = 100
                subJob.status = JobStatus.FINISHED
                hasBeenFinished = true
            }
            BinderPhases.FAILED.event -> {
                subJob.progress = 100
                hasBeenFinished = true
            }
        }
        subJobRepository.save(subJob)
        if (hasBeenFinished) {
            mainJobLogic.processMainJob(subJob.parent.id.toString())
        }
    }

    private fun calculatePushingProgress(layerProgress: ProgressEntry): Int {
        fun getLayerPushingProgress(progressObject: ProgressInfoObject): Int {
            return ((progressObject.current.toDouble() / progressObject.total) * 15).toInt()
        }

        var pushingProgress = 0
        pushingProgress += if (layerProgress.layer1.progressObject != null)
            getLayerPushingProgress(layerProgress.layer1.progressObject) else 15
        pushingProgress += if (layerProgress.layer2.progressObject != null)
            getLayerPushingProgress(layerProgress.layer2.progressObject) else 15
        pushingProgress += if (layerProgress.layer3.progressObject != null)
            getLayerPushingProgress(layerProgress.layer3.progressObject) else 15
        pushingProgress += if (layerProgress.layer4.progressObject != null)
            getLayerPushingProgress(layerProgress.layer4.progressObject) else 15

        return pushingProgress
    }
}

