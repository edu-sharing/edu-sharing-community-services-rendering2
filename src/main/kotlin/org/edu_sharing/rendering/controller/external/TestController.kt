package org.edu_sharing.rendering.controller.external

import org.edu_sharing.rendering.dto.TestResponse
import org.edu_sharing.rendering.service.TestService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/test")
class TestController(private val service: TestService) {

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun getMessage(): TestResponse {
        return service.getMessage()
    }
}