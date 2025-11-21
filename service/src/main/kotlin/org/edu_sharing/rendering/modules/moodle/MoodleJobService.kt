package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.RequestUserData
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

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

    fun createJob(node: Node, userData: RequestUserData, module: String, submitUserDetails: Boolean): String? {
        val job = mapper.nodeToRenderingJob(node = node, module = module, isConversionType = true)
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
            hash = node.content?.hash ?: "",
            title = node.title ?: "",
            authorityName = userData.authorityName,
            userEmail = if (submitUserDetails) (userData.userEMail ?: "") else "${UUID.randomUUID()}@${UUID.randomUUID()}.edu",
            userGivenName = if (submitUserDetails) (userData.firstName ?: "") else UUID.randomUUID().toString(),
            userSurname = if (submitUserDetails) (userData.surName ?: "") else UUID.randomUUID().toString()
        )
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)
        return job.id.toString()
    }
}
