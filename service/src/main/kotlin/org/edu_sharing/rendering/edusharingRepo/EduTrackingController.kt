package org.edu_sharing.rendering.edusharingRepo

import jakarta.validation.Valid
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.edusharingRepo.dto.TrackingRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController()
@RequestMapping("/public/tracking")
@ConditionalOnMaster
class EduTrackingController(
    private val trackingService: EduTrackingService,
) {

    @PutMapping()
    fun trackObject(@RequestBody @Valid body: TrackingRequest): ResponseEntity<Void> {
        trackingService.trackObject(
            event = body.eventType,
            objectId = body.nodeId,
            repoId = body.repoId
        )
        return ResponseEntity.ok().build()
    }
}
