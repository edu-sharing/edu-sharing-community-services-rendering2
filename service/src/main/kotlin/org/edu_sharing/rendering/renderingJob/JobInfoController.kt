package org.edu_sharing.rendering.renderingJob

import org.edu_sharing.rendering.config.annotation.ConditionalOnController
import org.edu_sharing.rendering.renderingJob.dto.JobInfoReply
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@ConditionalOnController
@RestController
@RequestMapping("/public/job")

class JobInfoController (
    private val service: JobInfoService,
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    fun getJobInfo(@RequestParam jobId: String): JobInfoReply {
        val job = service.getRenderingJob(jobId)
        return service.getJobInfo(job)
    }
}
