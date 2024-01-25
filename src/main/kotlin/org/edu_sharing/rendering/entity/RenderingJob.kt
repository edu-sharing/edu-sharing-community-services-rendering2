package org.edu_sharing.rendering.entity

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("RenderingJobs")
data class RenderingJob (
    val position: Int,
    val status: String
) {
    @get:Id
    var id: String? = null
}
