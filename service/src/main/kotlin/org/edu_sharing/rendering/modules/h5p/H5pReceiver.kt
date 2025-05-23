package org.edu_sharing.rendering.modules.h5p

import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.utils.combinePath
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@ConditionalOnConverter
@Component
class H5pReceiver(
    private val mainJobLogic: MainJobLogic,
    private val renderingJobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val h5pUploadService: H5pUploadService,
    private val mapper: Mapper,
    private val appInfo: AppInfo
){
    private val log = LoggerFactory.getLogger(H5pReceiver::class.java)

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
        var subJob = jobEntry.subJobs.first()
        subJob.status = SubJobStatus.PROCESSING
        jobEntry.status = RenderingJobStatus.PROCESSING
        renderingJobRepository.save(jobEntry)
        subJob = subJobRepository.save(subJob)
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        try {
            val contentId = h5pUploadService.getContentId(cacheObject)
            log.info("H5P retrieval or upload successful. Content id: {}", contentId)
            subJob.status = SubJobStatus.FINISHED
            subJob.message = appInfo.public.url.combinePath(H5P_BASE_PATH, contentId)
        } catch (exception: Exception) {
            log.error("H5P retrieval or upload failed with error: {}", exception.message, exception)
            subJob.status = SubJobStatus.FAILED
            subJob.errorMessage = GENERIC_CONVERSION_ERROR
        }
        subJobRepository.save(subJob)
        mainJobLogic.processMainJob(message.id)
    }
}
