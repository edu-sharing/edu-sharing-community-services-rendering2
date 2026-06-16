package org.edu_sharing.rendering.edusharingRepo

import jakarta.validation.Valid
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.edusharingRepo.dto.TrackingRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/public/tracking")
@ConditionalOnController
class EduTrackingController(
    private val trackingService: EduTrackingService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PutMapping()
    fun trackObject(@RequestBody @Valid body: TrackingRequest): ResponseEntity<Void> {
        log.debug("PUT /public/tracking event=${body.eventType}, nodeId=${body.nodeId}, repoId=${body.repoId}")
        trackingService.trackObject(
            event = body.eventType,
            objectId = body.nodeId,
            repoId = body.repoId
        )
        return ResponseEntity.ok().build()
    }
}
