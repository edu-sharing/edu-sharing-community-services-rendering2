package org.edu_sharing.rendering.modules.eduhtml

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.renderingJob.retrieveOrCreateDeduplicatedJob
import org.edu_sharing.rendering.storage.StaticStorageService
import org.edu_sharing.rendering.utils.storageNodeId
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class EduHtmlService(
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val amqpTemplate: AmqpTemplate,
    private val storageImplementation: StaticStorageService,
    private val subJobRepository: SubJobRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value($$"${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value($$"${app.queue.edu-html.key}")
    lateinit var jobRoutingKey: String

    companion object {
        const val MAIN_ENTITY_PROPERTY = "ccm:ccressourcemainentity"
        const val MAIN_ENTITY_KEY = "mainEntity"

        /**
         * Zero-byte object written as the **last** step of a successful extraction. A rendering is
         * only considered cached once this marker exists, which guards against two states in which
         * the entry point alone is present but the archive is not usable: an extraction that failed
         * part-way through (its already-uploaded objects used to be served forever, because the
         * entry point is typically near the top of the archive), and one that is still running.
         */
        const val COMPLETION_MARKER_PATH = ".rendering-complete"
    }

    fun createJob(node: Node, module: String): String {
        log.debug("Creating EduHTML job for nodeId ${node.ref.id}, module $module")
        return retrieveOrCreateDeduplicatedJob(
            findActiveJobId = {
                findActiveJobId(node)?.also { log.debug("Reusing existing EduHTML job {} for nodeId {}", it, node.ref.id) }
            },
            create = { createNewJob(node, module) }
        )
    }

    private fun createNewJob(node: Node, module: String): String {
        var job = mapper.nodeToRenderingJob(
            node = node,
            module = module,
            isConversionType = true
        )
        // Participate in the activeJobPerNodeHash unique index (one active job per node+hash).
        job.deduplicated = true
        job = jobRepository.save(job)

        val subJob = SubJob(
            routingKey = jobRoutingKey,
            parent = job
        )
        resolveMainEntity(node)?.let { subJob.additionalData = mapOf(MAIN_ENTITY_KEY to it) }
        subJobRepository.save(subJob)

        val message = RenderingJobMessage(id = job.id.toString())
        log.debug("Sending EduHTML job message for jobId {} to queue {}", job.id, jobRoutingKey)
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)

        return job.id.toString()
    }

    private fun findActiveJobId(node: Node): String? =
        jobRepository.findAllByEsObjectId(node.storageNodeId())
            .firstOrNull { it.status <= RenderingJobStatus.PROCESSING }
            ?.id?.toString()

    fun getObjectLink(cacheObject: CacheObject, candidates: List<String>): ObjectLink {
        log.debug("Looking up cached EduHTML entry point for nodeId {}, candidates {}", cacheObject.nodeId, candidates)
        if (!storageImplementation.objectExists(cacheObject, COMPLETION_MARKER_PATH)) {
            log.debug(
                "EduHTML rendering for nodeId {} has no completion marker, treating it as not cached",
                cacheObject.nodeId
            )
            throw ResourceNotFoundException("Resource ${cacheObject.nodeId} not cached")
        }
        val entryPath = candidates.firstOrNull { storageImplementation.objectExists(cacheObject, it) }
            ?: throw ResourceNotFoundException("Resource ${cacheObject.nodeId} not cached")

        return storageImplementation.getObjectLink(cacheObject, entryPath)
    }

    /**
     * Resolves the custom entry-point override from the node's [`ccm:ccressourcemainentity`]
     * property (OLD `mod_html::getIndexFileName`), or `null` if unset. The exact (double-s)
     * property spelling is intentional and must match the repository's metadata.
     */
    fun resolveMainEntity(node: Node): String? =
        node.properties?.getOrDefault(MAIN_ENTITY_PROPERTY, listOf(""))[0]
            ?.takeIf { it.isNotBlank() }
            ?.trimStart('/')

    /**
     * Prioritized list of archive-relative entry-point paths to look for: the custom override
     * if present, otherwise the conventional defaults including Articulate Storyline's `story.html`.
     */
    fun entryCandidates(mainEntity: String?): List<String> =
        mainEntity?.let { listOf(it) } ?: listOf("index.html", "index.htm", "story.html")
}
