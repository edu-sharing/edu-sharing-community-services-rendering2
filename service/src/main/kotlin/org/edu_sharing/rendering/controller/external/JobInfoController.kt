package org.edu_sharing.rendering.controller.external

import org.edu_sharing.rendering.dto.JobInfoReply
import org.edu_sharing.rendering.service.JobInfoService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/public/job")

class JobInfoController (private val service: JobInfoService) {
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun getJobInfo(@RequestParam jobId: String): JobInfoReply {
        return service.getJobInfo(jobId)
    }
}