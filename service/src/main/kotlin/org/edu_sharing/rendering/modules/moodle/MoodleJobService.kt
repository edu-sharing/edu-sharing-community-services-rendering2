package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.security.jwt.JWTBasedUserDetail
import org.edu_sharing.rendering.utils.SecurityContextUtils
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

@Service
class MoodleJobService(
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate,
    @param:Value($$"${app.queue.topicExchange}")
    val topicExchangeName: String,
    @param:Value($$"${app.queue.moodle.key}")
    val jobRoutingKey: String,
    @param:Value($$"${app.security.enabled}")
    private val securityEnabled: Boolean
) {
    fun createJob(node: Node, module: String): String? {
        val job = mapper.nodeToRenderingJob(node = node, module = module, isConversionType = true)
        jobRepository.save(job)

        val subJob = SubJob(
            status = SubJobStatus.QUEUED,
            routingKey = jobRoutingKey,
            parent = job
        )
        subJobRepository.save(subJob)

        val userDetails: JWTBasedUserDetail = if (securityEnabled) {
            SecurityContextUtils.currentUser()
        } else {
            // For local testing only
            JWTBasedUserDetail(
                username = "admin",
                email = "admin@edu-sharing.net",
                firstName = "Admin",
                lastName = "User",
                notBefore = Date(),
                expirationDate = Date(),
                repoId = "local",
                primaryAffiliation = "admin"
            )
        }

        val message = MoodleJobMessage(
            id = job.id.toString(),
            nodeId = job.esObjectId,
            hash = node.content?.hash ?: "",
            title = node.title ?: node.name,
            userName = userDetails.username,
            userEmail = userDetails.email.ifBlank { getFallbackMail() },
            firstName = userDetails.firstName,
            lastName = userDetails.lastName
        )
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)
        return job.id.toString()
    }

    private fun getFallbackMail(): String {
        val shortId = UUID.randomUUID().toString().replace("-", "").take(12)
        return "$shortId@$shortId.edu"
    }
}
