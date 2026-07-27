package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.utils.storageNodeId
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class H5pJobService(
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value($$"${app.queue.h5p.key}")
    lateinit var jobRoutingKey: String

    @Value($$"${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    fun createJob(node: Node, module: String): String {
        log.debug("Creating H5P job for nodeId={}, module={}", node.storageNodeId(), module)
        val existingJob = jobRepository.findAllByEsObjectId(node.storageNodeId())
            .firstOrNull { it.status <= RenderingJobStatus.PROCESSING }

        if (existingJob != null) {
            log.debug("Reusing existing H5P job {} for nodeId={}", existingJob.id, node.ref.id)
            return existingJob.id.toString()
        }

        var job = mapper.nodeToRenderingJob(
            node = node,
            module = module,
            isConversionType = true
        )
        job = jobRepository.save(job)

        val subJob = SubJob(
            routingKey = jobRoutingKey,
            parent = job
        )
        subJobRepository.save(subJob)

        val message = RenderingJobMessage(job.id.toString())
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)
        log.debug("H5P job {} enqueued on routingKey={}", job.id, jobRoutingKey)
        return job.id.toString()
    }
}
