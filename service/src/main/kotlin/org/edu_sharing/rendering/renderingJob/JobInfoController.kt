package org.edu_sharing.rendering.renderingJob

import org.edu_sharing.rendering.core.annotation.ConditionalOnController
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

    // Re-fetch fresh links for an existing job (short-lived module links, e.g. Sodix). Re-enqueues
    // the module's fetch and returns the now QUEUED/PROCESSING state; the client resumes its
    // /public/job polling to pick up the refreshed link.
    @PostMapping("/refresh", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    fun refresh(@RequestParam jobId: String): JobInfoReply {
        service.refreshLinks(service.getRenderingJob(jobId))
        return service.getJobInfo(service.getRenderingJob(jobId))
    }
}
