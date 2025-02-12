package org.edu_sharing.rendering.core

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@ConditionalOnController
@RequestMapping("/public/renderdata")
class RenderController (private val service: RenderDataService){

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getRenderData(
        @RequestBody @Valid body: RenderDataRequest
    ): ResponseEntity<RenderDataResponse> {
        return ResponseEntity
            .ok()
            .body(service.getRenderModule(body).handle(body))
    }
}
