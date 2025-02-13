package org.edu_sharing.rendering.modules.binder

import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class BinderJobService(
    private val mapper: Mapper,
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val amqpTemplate: AmqpTemplate
) {
    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    @Value("\${app.queue.binder.key}")
    lateinit var jobRoutingKey: String

    fun createJob(request: RenderDataRequest, module: String): String {
        val job = mapper.renderDataRequestToRenderingJob(request, module)
        jobRepository.save(job)
        val subJob = SubJob(
            status = JobStatus.QUEUED,
            parent = job,
            routingKey = jobRoutingKey
        )
        subJobRepository.save(subJob)
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, RenderingJobMessage(id = job.id.toString()))

        return job.id.toString()
    }
}