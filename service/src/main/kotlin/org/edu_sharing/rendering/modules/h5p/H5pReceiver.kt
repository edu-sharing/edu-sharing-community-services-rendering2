package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.config.annotation.ConditionalOnH5p
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@ConditionalOnH5p
@Component
class H5pReceiver(
    private val mainJobLogic: MainJobLogic,
    private val renderingJobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val h5pUploadService: H5pUploadService,
    private val mapper: Mapper
){
    private val log = LoggerFactory.getLogger(H5pReceiver::class.java)

    @Value("\${app.public.url}:\${app.public.port}")
    lateinit var baseUrl: String

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${app.queue.h5p.name}", durable = "false"),
                exchange = Exchange(name = "\${app.queue.topicExchange}", type = "topic"),
                key = ["\${app.queue.h5p.key}"]
            )
        ], containerFactory = "singlePrefetchConnectionFactory"
    )
    fun receiveMessage(message: RenderingJobMessage) {
        val jobEntry = mainJobLogic.getMainJobEntry(message.id)
        if (jobEntry == null || jobEntry.subJobs.isEmpty()) {
            log.error(if (jobEntry == null) "No job entry with id {}"
            else "Job entry with id {} has no sub jobs" , message.id)
            return
        }
        val subJob = jobEntry.subJobs.first()
        subJob.status = JobStatus.PROCESSING
        jobEntry.status = JobStatus.PROCESSING
        renderingJobRepository.save(jobEntry)
        subJobRepository.save(subJob)
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        try {
            val contentId = h5pUploadService.getContentId(cacheObject)
            log.info("H5P retrieval or upload successful. Content id: {}", contentId)
            subJob.status = JobStatus.FINISHED
            subJob.message = "$baseUrl$H5P_BASE_PATH/$contentId"
        } catch (exception: Exception) {
            log.error("H5P retrieval or upload failed with error: {}", exception.message)
            subJob.status = JobStatus.FAILED
            subJob.message = exception.message
        }
        subJobRepository.save(subJob)
        mainJobLogic.processMainJob(message.id)
    }
}