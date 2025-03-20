package org.edu_sharing.rendering.core

import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/public/ping")
@ConditionalOnMaster
class PingController {

    @GetMapping()
    @ResponseStatus(HttpStatus.OK)
    fun ping() {}
}