package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.utils.SecurityContextUtils
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SodixRenderModule(
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService,
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val amqpTemplate: AmqpTemplate,
    private val subJobRepository: SubJobRepository,
    @param:Value($$"${app.queue.topicExchange}")
    private val topicExchangeName: String,
    @param:Value($$"${app.queue.sodix.key}")
    private val jobRoutingKey: String,
    @param:Value($$"${app.session.sodix.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?
) : RenderModule, ThirdPartyModule {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val requiredCredentialKeys = setOf("baseurl")
        private val optionalCredentialKeys = setOf("playoutMimetypes", "allowExternalFrameSrc")
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime

    override fun module() = "SODIX"
    override fun isOptionalModule() = true
    override fun supportsLinkRefresh() = true

    override fun rendersDeferred(node: Node): Boolean {
        // Deferred = never embedded in an iframe, so the short-lived link is only needed on a later
        // download/open click: paid media, or a mimetype Sodix cannot play out (mirrors the
        // frontend's checkIfIframeAllowed()). The remaining (playable, non-paid) case may still
        // resolve to an external link, but that decision needs the fetched link, so it is not deferred.
        return isPaidMedia(node) || !isPlayable(node)
    }

    private fun isPaidMedia(node: Node): Boolean =
        node.properties?.getOrDefault("ccm:editorial_state", mutableListOf(""))[0] == "restricted_mz"

    private fun isPlayable(node: Node): Boolean {
        val playoutMimetypes = getCredentials(node.ref.repo)["playoutMimetypes"]
        if (playoutMimetypes.isNullOrBlank()) return true
        return Regex(playoutMimetypes).containsMatchIn(node.mimetype ?: "")
    }

    override fun handle(node: Node): RenderDataResponse {
        if (rendersDeferred(node)) {
            log.debug("Deferring Sodix link fetch for node ${node.ref.id}; client will fetch on demand")
            return RenderDataResponse(module = module(), deferred = true)
        }
        return fetchOnDemand(node)
    }

    override fun fetchOnDemand(node: Node): RenderDataResponse {
        log.debug("Handling Sodix node ${node.ref.id}, creating async job")
        val replicationSource = node.properties?.getOrDefault("ccm:replicationsource", mutableListOf(""))[0]
        val replicationSourceId = node.properties?.getOrDefault("ccm:replicationsourceid", mutableListOf(""))[0]
        if (replicationSource.isNullOrBlank() || replicationSourceId.isNullOrBlank()) {
            log.error("Missing replication source data in request. Node: " + node.ref.id)
            throw IllegalArgumentException()
        }
        val isPaidMedia = node.properties?.getOrDefault("ccm:editorial_state", mutableListOf(""))[0] == "restricted_mz"

        val job = mapper.nodeToRenderingJob(
            node = node,
            module = module(),
            isConversionType = true
        )
        // Persist the fetch inputs so an expired link can be re-fetched later (see refreshLinks).
        job.renderParams = mapOf(
            "identifier" to replicationSourceId,
            "isPaidMedia" to isPaidMedia.toString()
        )
        jobRepository.save(job)

        subJobRepository.save(SubJob(
            routingKey = jobRoutingKey,
            parent = job
        ))

        var role = ""
        if (isPaidMedia) {
            role = SecurityContextUtils.currentUser().primaryAffiliation
        }
        val message = SodixJobMessage(
            id = job.id.toString(),
            nodeId = job.esObjectId,
            identifier = replicationSourceId,
            isPaidMedia = isPaidMedia,
            role = role
        )
        log.debug("Sending Sodix job message for jobId ${job.id}, identifier $replicationSourceId, isPaidMedia $isPaidMedia to queue $jobRoutingKey")
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)

        return RenderDataResponse(
            jobId = job.id.toString(),
            module = module()
        )
    }

    override fun refreshLinks(renderingJob: RenderingJob) {
        val params = renderingJob.renderParams
            ?: throw IllegalArgumentException("Job ${renderingJob.id} has no stored render params; cannot refresh Sodix link")
        val identifier = params["identifier"]
            ?: throw IllegalArgumentException("Job ${renderingJob.id} is missing the Sodix identifier; cannot refresh link")
        val isPaidMedia = params["isPaidMedia"].toBoolean()

        val playoutSubJob = renderingJob.subJobs.firstOrNull { it.quality == 0 }
            ?: throw IllegalArgumentException("Job ${renderingJob.id} has no Sodix playout sub-job to refresh")

        var role = ""
        if (isPaidMedia) {
            role = SecurityContextUtils.currentUser().primaryAffiliation
        }

        // Move the job out of any final state FIRST, so a poll landing mid-refresh can never observe a
        // FINISHED job with a re-QUEUED sub-job (the client would treat that as "done" and stop polling).
        jobRepository.updateStatusWithoutVersion(renderingJob.id, RenderingJobStatus.PROCESSING)

        // Atomically claim the refresh: flip the sub-job from a terminal state back to QUEUED in a single
        // conditional update. Only one concurrent caller can win; a false result means a refresh (or the
        // initial fetch) is already running for this job, so we must NOT enqueue a duplicate message.
        if (!subJobRepository.claimForRefresh(playoutSubJob.id)) {
            log.debug("Sodix refresh for job ${renderingJob.id} skipped, a refresh is already in progress")
            return
        }

        val message = SodixJobMessage(
            id = renderingJob.id.toString(),
            nodeId = renderingJob.esObjectId,
            identifier = identifier,
            isPaidMedia = isPaidMedia,
            role = role
        )
        log.debug("Re-enqueuing Sodix job message for jobId ${renderingJob.id}, identifier $identifier, isPaidMedia $isPaidMedia to queue $jobRoutingKey")
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)
    }

    override fun validateThirdPartyCredentials(
        credentials: Map<String, String>,
        repoId: String
    ) {
        validateCredentials(
            credentials = credentials,
            requiredCredentialKeys = requiredCredentialKeys.filterNot { optionalCredentialKeys.contains(it) }.toSet(),
            moduleName = module()
        )
    }

    override fun getCredentials(repoId: String): Map<String, String> {
        val registration = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .orElseThrow { IllegalArgumentException("Unknown repository id: $repoId") }
        return registration.module[module()]?.credentials ?: mapOf()
    }

    override fun getObjectLinkFromJobData(subJob: SubJob, renderingJob: RenderingJob): ObjectLink? {
        return subJob.message?.let { ObjectLink(link = it) }
    }

    override fun getAdditionalData(subJob: SubJob, repoId: String): Map<String, String>? {
        val clientSettings = getCredentials(repoId).filter { optionalCredentialKeys.contains(it.key) }
        val subJobAdditionalData = subJob.additionalData ?: emptyMap()

        return clientSettings + subJobAdditionalData
    }
}
