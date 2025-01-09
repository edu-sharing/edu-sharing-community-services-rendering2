package org.edu_sharing.rendering.core

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@ConditionalOnController
@RequestMapping("/public/renderdata")
class RenderController (private val service: RenderDataService){

    private val log = LoggerFactory.getLogger(javaClass)

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getRenderData(
        @RequestBody @Valid body: RenderDataRequest,
        @RequestHeader headers: Map<String, String>
    ): ResponseEntity<RenderDataResponse> {
        log.info(headers["authorization"])
        return ResponseEntity
            .ok()
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,"ES_RENDER_SSID")
            .header("ES_RENDER_SSID", "SES123123")
            .body(service.getRenderData(body))
    }
}
