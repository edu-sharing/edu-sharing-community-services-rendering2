package org.edu_sharing.rendering.dto.queue

/**
 * Data Class RenderingJobMessage
 *
 * I am the queue message for the rendering main job
 * my attribute id references a document in the rendering_job collection
 */
data class RenderingJobMessage(
    val id: String
)
