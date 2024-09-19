package org.edu_sharing.rendering.modules.av

import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.renderingJob.entity.SubJob

interface AvConversionService {
    fun convert(cacheObject: CacheObject, subJob: SubJob)
}