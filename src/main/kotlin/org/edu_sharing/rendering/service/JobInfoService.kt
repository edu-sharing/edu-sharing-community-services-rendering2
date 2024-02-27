package org.edu_sharing.rendering.service

import org.bson.types.ObjectId
import org.edu_sharing.rendering.dto.JobInfoReply
import org.edu_sharing.rendering.dto.JobProgressInfo
import org.edu_sharing.rendering.exception.EntryNotFoundException
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class JobInfoService (
    private val jobRepository: RenderingJobRepository
){
    fun getJobInfo(jobId: String): JobInfoReply {
        val job = jobRepository.findByIdOrNull(ObjectId(jobId)) ?: throw EntryNotFoundException("Invalid jobId: $jobId")


        val info = JobProgressInfo(1, 1, "link")
        return JobInfoReply(mutableListOf(info))
    }
}