package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
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
    @Value("\${app.queue.h5p.key}")
    lateinit var jobRoutingKey: String

    @Value("\${app.queue.topicExchange}")
    lateinit var topicExchangeName: String

    fun createJob(request: RenderDataRequest, module: RenderModules): String {
        val existingJob = jobRepository.findAllByEsObjectId(request.nodeId)
            .firstOrNull { it.status <= JobStatus.PROCESSING }

        if (existingJob != null) {
            return existingJob.id.toString()
        }

        val job = mapper.renderDataRequestToRenderingJob(request, module)
        jobRepository.save(job)

        val subJob = SubJob(
            status = JobStatus.QUEUED,
            routingKey = jobRoutingKey,
            parent = job
        )
        subJobRepository.save(subJob)

        val message = RenderingJobMessage(job.id.toString())
        amqpTemplate.convertAndSend(topicExchangeName, jobRoutingKey, message)
        return job.id.toString()
    }
}
