package org.edu_sharing.rendering.modules.ddb

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class DdbJobService(
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate
) {
    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value("\${app.queue.ddb.key}")
    lateinit var jobRoutingKey: String

    fun createJob(node: Node, module: String): String {
        val remoteId = node.remote?.id ?: throw IllegalArgumentException("Node $node does not have a remoteId")
        val job = mapper.nodeToRenderingJob(node, module, true)
        jobRepository.save(job)
        val subJob = SubJob(
            status = JobStatus.QUEUED,
            parent = job,
            routingKey = jobRoutingKey
        )
        subJobRepository.save(subJob)
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, DdbJobMessage(id = job.id.toString(), remoteId = remoteId))

        return job.id.toString()
    }
}