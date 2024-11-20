package org.edu_sharing.rendering.modules.binder.dto

import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class ProgressEntry(
    @JsonDeserialize(using = ProgressInfoDeserializer::class)
    val layer1: ProgressInfo,
    @JsonDeserialize(using = ProgressInfoDeserializer::class)
    val layer2: ProgressInfo,
    @JsonDeserialize(using = ProgressInfoDeserializer::class)
    val layer3: ProgressInfo,
    @JsonDeserialize(using = ProgressInfoDeserializer::class)
    val layer4: ProgressInfo
)
