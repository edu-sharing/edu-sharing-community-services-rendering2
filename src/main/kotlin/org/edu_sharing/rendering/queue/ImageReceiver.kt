package org.edu_sharing.rendering.queue

import org.bson.types.ObjectId
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.queue.SubJobMessage
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.service.ImageConversionService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ImageReceiver(
    private val mongoRepo: RenderingJobRepository,
    private val conversionService: ImageConversionService
) {
    fun receiveMessage(message: SubJobMessage) {
        this.conversionService.reset()
        println("received sub job message")
        val jobEntry = mongoRepo.findByIdOrNull(ObjectId(message.id)) ?: return
        val cacheObject = CacheObject(
            nodeId = jobEntry.esObjectId,
            type = jobEntry.esObjectType,
            hash = jobEntry.esHash,
            mimeType = jobEntry.mimeType
        )
        jobEntry.subJobs.forEach {
            try {
                it.status = JobStatus.PROCESSING
                mongoRepo.save(jobEntry)
                this.conversionService.convert(cacheObject, it.quality)
                it.status = JobStatus.FINISHED
            } catch (exception: Exception) {
                it.status = JobStatus.FAILED
            }
        }
        jobEntry.finishedTimestamp = System.currentTimeMillis()
        mongoRepo.save(jobEntry)
    }
}