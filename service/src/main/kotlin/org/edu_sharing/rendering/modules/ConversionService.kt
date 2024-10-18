package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob

interface ConversionService {
    fun process(cacheObject: CacheObject, renderingJob: RenderingJob)
}