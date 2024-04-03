package org.edu_sharing.rendering.entity

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("MoodleCourse")
data class MoodleCourse(
    @Id
    val nodeId: String,
    val courseId: Int
)
