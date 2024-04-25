package org.edu_sharing.rendering.controller.external

import org.bson.types.ObjectId
import org.edu_sharing.rendering.config.annotation.ConditionalOnController
import org.edu_sharing.rendering.dto.JobInfoReply
import org.edu_sharing.rendering.exception.EntryNotFoundException
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.edu_sharing.rendering.service.JobInfoService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@ConditionalOnController
@RestController
@RequestMapping("/public/job")

class JobInfoController (
    private val service: JobInfoService,
    private val jobRepository: RenderingJobRepository
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    fun getJobInfo(@RequestParam jobId: String): JobInfoReply {
        val job = jobRepository.findByIdOrNull(ObjectId(jobId)) ?: throw EntryNotFoundException("Invalid jobId: $jobId")
        return service.getJobInfo(job)
    }
}