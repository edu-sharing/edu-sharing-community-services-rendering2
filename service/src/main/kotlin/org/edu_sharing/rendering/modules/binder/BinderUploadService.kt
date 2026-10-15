package org.edu_sharing.rendering.modules.binder

import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.utils.LogSanitizer
import org.springframework.beans.factory.annotation.Qualifier
import reactor.core.Disposable
import reactor.core.publisher.Flux
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
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException

@Service
class BinderUploadService(
    private val subJobRepository: SubJobRepository,
    private val mainJobLogic: BinderMainJobLogic,
    private val moduleRegistry: ModuleRegistry,
    private val gitServiceRegistry: GitServiceRegistry,
    @param:Qualifier("longRunningWebClientBuilder")
    private val webClientBuilder: WebClient.Builder,
    @param:Value($$"${app.module.binder.sse-idle-timeout:PT15M}")
    private val sseIdleTimeout: Duration
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun process(
        cacheObject: CacheObject, uploadSubJob: SubJob, module: String
    ) {
        log.debug("Binder upload process started for nodeId={}, externalUrl={}", cacheObject.nodeId, cacheObject.externalUrl)
        var binderUploadSubJob = uploadSubJob
        val type
                : ParameterizedTypeReference<ServerSentEvent<BinderSseEvent>> =
            object : ParameterizedTypeReference<ServerSentEvent<BinderSseEvent>>() {}

        try {
            val gitService = gitServiceRegistry.getService(cacheObject.externalUrl ?: "")
                ?: throw IllegalStateException("Git service for url not found: ${cacheObject.externalUrl ?: ""}. This should NOT happen at this point")
            val gitDetails = gitService.getGitDetailsFromUrl(cacheObject.externalUrl ?: "")
            binderUploadSubJob.status = SubJobStatus.PROCESSING
            binderUploadSubJob.processingStartedDate = Instant.now()
            binderUploadSubJob.message = "Initializing binder import"
            binderUploadSubJob = subJobRepository.save(binderUploadSubJob)
            val binderWebClient = getWebclient(module = module, repoId = cacheObject.repoId)
            log.debug("Opening Binder SSE stream: /build/gh/{}/{}/{}", gitDetails.user, gitDetails.repo, gitDetails.branch)

            val eventStream = binderWebClient.get()
                .uri("/build/gh/${gitDetails.user}/${gitDetails.repo}/${gitDetails.branch}")
                .retrieve()
                .bodyToFlux(type)

            consumeEvents(eventStream, binderUploadSubJob.id, gitDetails)
        } catch (exception: Exception) {
            log.error("Error creating jupyterHub URL: ${LogSanitizer.sanitize(exception.message)}", LogSanitizer.sanitize(exception))
            binderUploadSubJob.status = SubJobStatus.FAILED
            binderUploadSubJob.finishedDate = Instant.now()
            binderUploadSubJob.message = "Error creating jupyterHub URL: " + LogSanitizer.sanitize(exception.message)
            binderUploadSubJob = subJobRepository.save(binderUploadSubJob)
            mainJobLogic.processMainJob(binderUploadSubJob.parent.id.toString())
        }
    }

    /**
     * Subscribes to the Binder build events. Whatever ends the stream - an error, an idle timeout, or the
     * server closing it without having sent READY/FAILED - leaves the sub-job finalized as FAILED and the
     * main job re-evaluated, so no job is left in PROCESSING forever.
     */
    internal fun consumeEvents(
        events: Flux<ServerSentEvent<BinderSseEvent>>,
        subJobId: ObjectId,
        gitDetails: GitDetails
    ): Disposable =
        events
            // Idle timeout: maximum silence between two events (not a limit on the total build time).
            .timeout(sseIdleTimeout)
            .subscribe(
                { content ->
                    // Never log the payload: the READY event carries the access token of the Binder session.
                    log.debug("Binder event: name[{}], id[{}], phase[{}]", content.event(), content.id(), content.data()?.phase)
                    updateSubJob(
                        eventData = content.data() ?: BinderSseEvent(phase = "", message = ""),
                        subJobId = subJobId,
                        gitDetails = gitDetails
                    )
                },
                { error ->
                    val reason = if (error is TimeoutException) {
                        "no event within $sseIdleTimeout"
                    } else {
                        LogSanitizer.sanitize(error.message)
                    }
                    log.error("Error receiving SSE: $reason", LogSanitizer.sanitize(error))
                    failIfUnfinished(subJobId, "Error receiving SSE: $reason")
                },
                {
                    log.info("SSE Server emitted completion event.")
                    failIfUnfinished(subJobId, "Binder closed the event stream without a final status")
                }
            )

    /** Marks the sub-job FAILED unless it already reached a terminal status, then re-evaluates the main job. */
    private fun failIfUnfinished(subJobId: ObjectId, message: String) {
        try {
            val subJob = subJobRepository.findByIdOrNull(subJobId)
            if (subJob == null) {
                log.warn("Cannot finalize Binder sub-job {}: not found", subJobId)
                return
            }
            if (subJob.status >= SubJobStatus.FINISHED) {
                return
            }
            subJob.status = SubJobStatus.FAILED
            subJob.finishedDate = Instant.now()
            subJob.message = message
            subJob.errorMessage = GENERIC_CONVERSION_ERROR
            subJobRepository.save(subJob)
            mainJobLogic.processMainJob(subJob.parent.id.toString())
        } catch (exception: Exception) {
            log.error("Could not finalize Binder sub-job $subJobId after stream end", exception)
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
                subJob.finishedDate = Instant.now()
                subJob.message = link
                hasBeenFinished = true
            }

            BinderPhases.FAILED.event -> {
                subJob.progress = 100
                subJob.status = SubJobStatus.FAILED
                subJob.finishedDate = Instant.now()
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
        // The long-running builder carries the connector, tracing filter and codec limit of the shared config.
        return webClientBuilder.clone()
            .baseUrl(baseUrl)
            .build()
    }
}
