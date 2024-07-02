package org.edu_sharing.rendering.processing.av

import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.entity.SubJob

interface AvConversionService {
    fun convert(cacheObject: CacheObject, subJob: SubJob)
}