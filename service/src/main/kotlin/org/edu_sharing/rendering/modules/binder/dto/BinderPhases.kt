package org.edu_sharing.rendering.modules.binder.dto

enum class BinderPhases(val event: String) {
    FAILED("failed"),
    BUILT("built"),
    WAITING("waiting"),
    BUILDING("building"),
    FETCHING("fetching"),
    PUSHING("pushing"),
    LAUNCHING("launching"),
    READY("ready")
}