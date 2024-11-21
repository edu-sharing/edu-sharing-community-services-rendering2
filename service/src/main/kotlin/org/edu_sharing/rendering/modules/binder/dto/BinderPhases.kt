package org.edu_sharing.rendering.modules.binder.dto

enum class BinderPhases(val event: String) {
    FAILED("Failed"),
    BUILT("Built"),
    WAITING("Waiting"),
    BUILDING("Building"),
    FETCHING("Fetching"),
    PUSHING("Pushing"),
    LAUNCHING("Launching"),
    READY("Ready")
}