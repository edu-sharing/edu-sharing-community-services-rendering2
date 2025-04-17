package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.RequestUserData
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class MoodleJobService(
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value("\${app.queue.moodle.key}")
    lateinit var jobRoutingKey: String

    fun createJob(node: Node, userData: RequestUserData, module: String): String? {
        val job = mapper.nodeToRenderingJob(node, module)
        jobRepository.save(job)

        val subJob = SubJob(
            status = SubJobStatus.QUEUED,
            routingKey = jobRoutingKey,
            parent = job
        )
        subJobRepository.save(subJob)

        val message = MoodleJobMessage(
            id = job.id.toString(),
            nodeId = job.esObjectId,
            title = node.title,
            authorityName = userData.authorityName,
            userEmail = userData.userEMail,
            userGivenName = userData.firstName,
            userSurname = userData.surName
        )
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)
        return job.id.toString()
    }
}
