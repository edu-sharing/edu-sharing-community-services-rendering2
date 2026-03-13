package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.security.jwt.JWTBasedUserDetail
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class MoodleJobService(
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate
) {
    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value("\${app.queue.moodle.key}")
    lateinit var jobRoutingKey: String

    fun createJob(node: Node, module: String): String? {
        val job = mapper.nodeToRenderingJob(node = node, module = module, isConversionType = true)
        jobRepository.save(job)

        val subJob = SubJob(
            status = SubJobStatus.QUEUED,
            routingKey = jobRoutingKey,
            parent = job
        )
        subJobRepository.save(subJob)

        val authentication = SecurityContextHolder.getContext().authentication
        val userDetails = authentication.principal as JWTBasedUserDetail

        val message = MoodleJobMessage(
            id = job.id.toString(),
            nodeId = job.esObjectId,
            hash = node.content?.hash ?: "",
            title = node.title ?: "",
            userName = userDetails.username,
            userEmail = userDetails.email,
            firstName = userDetails.firstName,
            lastName = userDetails.lastName
        )
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)
        return job.id.toString()
    }
}
