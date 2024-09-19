package org.edu_sharing.rendering.renderingJobs.queue

/**
 * Data Class SubJobMessage
 *
 * I am the queue message for the sub rendering jobs (conversion)
 * my attribute id references a document in the rendering_job collection
 * my attribute quality tells my consumer which quality is requested
 */
data class SubJobMessage(
    val id: String,
    val quality: Int = 0
)
