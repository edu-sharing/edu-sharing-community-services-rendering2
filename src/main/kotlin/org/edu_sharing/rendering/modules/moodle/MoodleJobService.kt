package org.edu_sharing.rendering.modules.moodle

import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.MoodleJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
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

    @Value("\${app.moodle.token}")
    lateinit var token: String

    @Value("\${app.moodle.categoryid}")
    lateinit var categoryId: String

    @Value("\${edu_sharing.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value("\${edu_sharing.queue.moodle.key}")
    lateinit var jobRoutingKey: String

    fun createJob(request: RenderDataRequest, module: RenderModules): String? {
        try {
            checkPrerequisites()
        } catch (exception: IllegalStateException) {
            log.warn("Could not create Moodle course creation job: " + exception.message)
            return null
        }
        if (request.userData == null) {
            log.error("Missing user data in Moodle request. Node: " + request.nodeId)
            throw IllegalArgumentException()
        }
        val job = mapper.cacheObjectToRenderingJob(mapper.renderDataRequestToCacheObject(request), module)
        jobRepository.save(job)
        val subJob = SubJob(
            status = JobStatus.QUEUED,
            routingKey = jobRoutingKey,
            parent = job
        )
        subJobRepository.save(subJob)
        val message = MoodleJobMessage(
            id = job.id.toString(),
            nodeId = job.esObjectId,
            title = request.title ?: "",
            authorityName = request.userData.authorityName,
            userEmail = request.userData.userEMail,
            userGivenName = request.userData.firstName,
            userSurname = request.userData.surName
        )
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)
        return job.id.toString()
    }

    private fun checkPrerequisites() {
        if (token.isBlank() || categoryId.isBlank()) {
            throw IllegalStateException("Moodle config invalid and/or incomplete")
        }
    }

}