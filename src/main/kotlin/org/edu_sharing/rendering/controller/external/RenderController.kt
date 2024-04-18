package org.edu_sharing.rendering.controller.external

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.annotation.PostConstruct
import jakarta.validation.Valid
import org.edu_sharing.rendering.config.annotation.ConditionalOnController
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.service.RenderDataService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@ConditionalOnController
@RestController
@RequestMapping("/public/renderdata")
class RenderController (private val service: RenderDataService){

    private val log = LoggerFactory.getLogger(javaClass)

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getRenderData(@RequestBody @Valid body: RenderDataRequest): RenderDataResponse {
        return service.getRenderData(body)
    }

    @PostConstruct
    fun test() {
        log.info("CONTROLLLEEEER")
    }
}