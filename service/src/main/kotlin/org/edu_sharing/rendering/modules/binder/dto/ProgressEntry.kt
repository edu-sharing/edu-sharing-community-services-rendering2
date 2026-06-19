package org.edu_sharing.rendering.modules.binder.dto

import tools.jackson.databind.annotation.JsonDeserialize

data class ProgressEntry(
    @param:JsonDeserialize(using = ProgressInfoDeserializer::class)
    val layer1: ProgressInfo,
    @param:JsonDeserialize(using = ProgressInfoDeserializer::class)
    val layer2: ProgressInfo,
    @param:JsonDeserialize(using = ProgressInfoDeserializer::class)
    val layer3: ProgressInfo,
    @param:JsonDeserialize(using = ProgressInfoDeserializer::class)
    val layer4: ProgressInfo
)
