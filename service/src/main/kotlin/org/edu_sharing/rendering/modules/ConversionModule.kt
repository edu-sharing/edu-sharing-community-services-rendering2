package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.queue.RenderingJobMessage

interface ConversionModule {
    fun createJob(renderingJob: RenderingJob, message: RenderingJobMessage)
}