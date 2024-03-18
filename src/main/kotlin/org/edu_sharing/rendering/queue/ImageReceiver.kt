package org.edu_sharing.rendering.queue

import org.bson.types.ObjectId
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.dto.queue.SubJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.edu_sharing.rendering.service.ImageConversionService
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ImageReceiver(
    private val jobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository,
    private val conversionService: ImageConversionService,
    private val mapper: Mapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun receiveMessage(message: SubJobMessage) {
        val jobEntry = jobRepository.findByIdOrNull(ObjectId(message.id))
        if (jobEntry == null) {
            logger.warn("Expected main job not found: " + message.id)
            return
        }
        val cacheObject = mapper.renderingJobToCacheObject(jobEntry)
        val sourceImage = conversionService.fetchSourceImage(cacheObject)
        jobEntry.subJobs.forEach {
            try {
                it.status = JobStatus.PROCESSING
                subJobRepository.save(it)
                this.conversionService.convert(cacheObject, it.quality, sourceImage)
                it.status = JobStatus.FINISHED
            } catch (exception: Exception) {
                logger.warn(exception.message)
                it.status = JobStatus.FAILED
            }
            subJobRepository.save(it)
        }
        jobEntry.finishedTimestamp = System.currentTimeMillis()
        jobRepository.save(jobEntry)
    }
}