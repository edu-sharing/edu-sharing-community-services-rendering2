package org.edu_sharing.rendering.modules.binder

import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.modules.binder.dto.*
import org.edu_sharing.rendering.modules.binder.git.GitServiceRegistry
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalTime
import java.util.function.Consumer

@Service
class BinderUploadService(
    private val subJobRepository: SubJobRepository,
    private val mainJobLogic: BinderMainJobLogic,
    private val moduleRegistry: ModuleRegistry,
    private val gitServiceRegistry: GitServiceRegistry,
    @param:Value($$"${spring.http.codecs.max-in-memory-size}")
    private val maxInMemorySize: DataSize
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun process(
        cacheObject: CacheObject, uploadSubJob: SubJob, module: String
    ) {
        var binderUploadSubJob = uploadSubJob
        val type
                : ParameterizedTypeReference<ServerSentEvent<BinderSseEvent?>?> =
            object : ParameterizedTypeReference<ServerSentEvent<BinderSseEvent?>?>() {}

        try {
            val gitService = gitServiceRegistry.getService(cacheObject.externalUrl ?: "")
                ?: throw IllegalStateException("Git service for url not found: ${cacheObject.externalUrl ?: ""}. This should NOT happen at this point")
            val gitDetails = gitService.getGitDetailsFromUrl(cacheObject.externalUrl ?: "")
            binderUploadSubJob.status = SubJobStatus.PROCESSING
            binderUploadSubJob.message = "Initializing binder import"
            binderUploadSubJob = subJobRepository.save(binderUploadSubJob)
            val binderWebClient = getWebclient(module = module, repoId = cacheObject.repoId)

            val eventStream = binderWebClient.get()
                .uri("/build/gh/${gitDetails.user}/${gitDetails.repo}/${gitDetails.branch}")
                .retrieve()
                .bodyToFlux<ServerSentEvent<BinderSseEvent?>?>(type)

            eventStream.subscribe(
                Consumer { content: ServerSentEvent<BinderSseEvent?>? ->
                    log.info(
                        "Time: {} - event: name[{}], id [{}], content[{}] ",
                        LocalTime.now(), content!!.event(), content.id(), content.data()
                    )
                    updateSubJob(
                        eventData = content.data() ?: BinderSseEvent(phase = "", message = ""),
                        subJobId = binderUploadSubJob.id,
                        gitDetails = gitDetails
                    )
                },
                Consumer { error: Throwable? ->
                    log.error("Error receiving SSE: ", error)
                    binderUploadSubJob.status = SubJobStatus.FAILED
                    binderUploadSubJob.message = "Error receiving SSE " + error?.message
                    binderUploadSubJob = subJobRepository.save(binderUploadSubJob)
                }
            ) {
                log.info("SSE Server emitted completion event.")
            }
        } catch (exception: Exception) {
            log.error("Error creating jupyterHub URL: ", exception)
            binderUploadSubJob.status = SubJobStatus.FAILED
            binderUploadSubJob.message = "Error creating jupyterHub URL: " + exception.message
            binderUploadSubJob = subJobRepository.save(binderUploadSubJob)
            mainJobLogic.processMainJob(binderUploadSubJob.parent.id.toString())
        }
    }

    private fun updateSubJob(eventData: BinderSseEvent, subJobId: ObjectId, gitDetails: GitDetails) {
        if (eventData.phase.isNullOrBlank()) {
            return
        }
        val subJob =
            subJobRepository.findByIdOrNull(subJobId) ?: throw IllegalStateException("SubJob $subJobId does not exist")
        var hasBeenFinished = false
        when (eventData.phase) {
            BinderPhases.WAITING.event -> {
                subJob.progress = 5
            }

            BinderPhases.FETCHING.event -> {
                subJob.progress = 10
            }

            BinderPhases.BUILDING.event -> {
                subJob.progress = 15
            }

            BinderPhases.PUSHING.event -> {
                if (eventData.progress != null) {
                    subJob.progress = 15 + calculatePushingProgress(eventData.progress)
                }
            }

            BinderPhases.BUILT.event -> {
                subJob.progress = 75
            }

            BinderPhases.LAUNCHING.event -> {
                subJob.progress = 90
            }

            BinderPhases.READY.event -> {
                var link = eventData.url
                if (gitDetails.filePath != null) {
                    link = link.plus("/doc/tree/${gitDetails.filePath}")
                }
                link = link.plus("?token=${eventData.token}")
                subJob.progress = 100
                subJob.status = SubJobStatus.FINISHED
                subJob.message = link
                hasBeenFinished = true
            }

            BinderPhases.FAILED.event -> {
                subJob.progress = 100
                subJob.status = SubJobStatus.FAILED
                subJob.message = eventData.message
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
        pushingProgress += if (layerProgress.layer1.progressObject != null) getLayerPushingProgress(layerProgress.layer1.progressObject) else 15
        pushingProgress += if (layerProgress.layer2.progressObject != null) getLayerPushingProgress(layerProgress.layer2.progressObject) else 15
        pushingProgress += if (layerProgress.layer3.progressObject != null) getLayerPushingProgress(layerProgress.layer3.progressObject) else 15
        pushingProgress += if (layerProgress.layer4.progressObject != null) getLayerPushingProgress(layerProgress.layer4.progressObject) else 15

        return pushingProgress
    }

    private fun getWebclient(module: String, repoId: String): WebClient {
        val module = moduleRegistry.getRenderModule<RenderModule>(module)
        if (module !is ThirdPartyModule) {
            throw IllegalArgumentException("Unexpected module type: ${module::class.java}")
        }
        val config = module.getCredentials(repoId)
        val baseUrl = config["baseurl"] ?: throw IllegalArgumentException("baseurl must be provided")
        return WebClient.builder()
            .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemorySize.toBytes().toInt()) }
            .baseUrl(baseUrl)
            .build()
    }
}
