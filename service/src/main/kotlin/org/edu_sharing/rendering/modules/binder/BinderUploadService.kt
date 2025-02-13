package org.edu_sharing.rendering.modules.binder

import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.modules.ConversionService
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.modules.binder.dto.BinderPhases
import org.edu_sharing.rendering.modules.binder.dto.BinderSseEvent
import org.edu_sharing.rendering.modules.binder.dto.ProgressEntry
import org.edu_sharing.rendering.modules.binder.dto.ProgressInfoObject
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalTime
import java.util.function.Consumer
import java.util.regex.Matcher
import java.util.regex.Pattern

@Service
class BinderUploadService(
    private val subJobRepository: SubJobRepository,
    private val mainJobLogic: MainJobLogic,
    private val moduleRegistry: ModuleRegistry,
) : ConversionService {

    private data class GitDetails(
        val user: String,
        val repo: String,
        val branch: String,
    )

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun process(
        cacheObject: CacheObject, renderingJob: RenderingJob
    ) {
        val gitDetails = getGitDetailsFromUrl(cacheObject.externalUrl ?: "")
        if (renderingJob.subJobs.isEmpty()) {
            return
        }
        var subJob = renderingJob.subJobs.first()
        subJob.status = JobStatus.PROCESSING
        subJob.message = "Initializing binder import"
        subJob = subJobRepository.save(subJob)

        val type
                : ParameterizedTypeReference<ServerSentEvent<BinderSseEvent?>?> =
            object : ParameterizedTypeReference<ServerSentEvent<BinderSseEvent?>?>() {}

        val binderWebClient = getWebclient(renderingJob)

        val eventStream = binderWebClient.get()
            .uri("/build/gh/${gitDetails.user}/${gitDetails.repo}/${gitDetails.branch}")
            .retrieve()
            .bodyToFlux<ServerSentEvent<BinderSseEvent?>?>(type)


        eventStream.subscribe(
            Consumer { content: ServerSentEvent<BinderSseEvent?>? ->
                logger.info(
                    "Time: {} - event: name[{}], id [{}], content[{}] ",
                    LocalTime.now(), content!!.event(), content.id(), content.data()
                )
                updateSubJob(content.data() ?: BinderSseEvent(phase = "", message = ""), subJob.id)
            },
            Consumer {
                error: Throwable? ->
                logger.error("Error receiving SSE: ", error)
                subJob.status = JobStatus.FAILED
                subJob.message = "Error receiving SSE " +  error?.message
                subJob = subJobRepository.save(subJob)
             },
            Runnable {
                logger.info("SSE Server emitted completion event.")
            }
        )
    }

    private fun updateSubJob(eventData: BinderSseEvent, subJobId: ObjectId) {
        var subJob = subJobRepository.findByIdOrNull(subJobId) ?: throw IllegalStateException("SubJob $subJobId does not exist")
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
                subJob.progress = 100
                subJob.status = JobStatus.FINISHED
                subJob.message = "${eventData.url}?token=${eventData.token}"
                hasBeenFinished = true
            }

            BinderPhases.FAILED.event -> {
                subJob.progress = 100
                subJob.status = JobStatus.FAILED
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

    private fun getGitDetailsFromUrl(gitHubUrl: String): GitDetails {
        val pattern: Pattern = Pattern.compile("https://github\\.com/([^/]+)/([^/]+)(?:/tree/([^/]+))?")
        val matcher: Matcher = pattern.matcher(gitHubUrl)

        if (!matcher.find()) {
            throw IllegalArgumentException("GitHub URL for Binder import must contain user and repo")
        }

        val user = matcher.group(1) ?: throw IllegalArgumentException("GitHub URL for Binder must contain user")
        val repository =
            matcher.group(2) ?: throw IllegalArgumentException("GitHub URL for Binder must contain repository")
        val branch = if (matcher.group(3) != null) matcher.group(3) else "main"

        return GitDetails(
            user = user,
            repo = repository,
            branch = branch
        )
    }

    private fun getWebclient(renderingJob: RenderingJob): WebClient {
        val module = moduleRegistry.getRenderModule<RenderModule>(renderingJob.module)
        if (module !is ThirdPartyModule) {
            throw IllegalArgumentException("Unexpected module type: ${module::class.java}")
        }
        val config = module.getConfig(renderingJob.repoId)
        val baseUrl = config["baseurl"] ?: throw IllegalArgumentException("baseurl must be provided")
        return WebClient.builder().baseUrl(baseUrl).build()
    }
}
