package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage

/**
 * Interface ConversionModule
 *
 * This interface has to be implemented by modules requiring conversion before
 * caching. The createJob method has to create a rendering job and publish it
 * to the respective queue.
 */
interface ConversionModule {
    fun createJob(renderingJob: RenderingJob, message: RenderingJobMessage)
}