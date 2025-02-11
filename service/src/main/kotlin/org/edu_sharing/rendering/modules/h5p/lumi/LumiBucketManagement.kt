package org.edu_sharing.rendering.modules.h5p.lumi

import org.edu_sharing.rendering.storage.BucketManagement
import org.edu_sharing.rendering.storage.StorageInfo
import org.springframework.stereotype.Component

@Component
class LumiBucketManagement(
    private val lumiContentManagementService: LumiContentManagementService,
): BucketManagement {
    override fun bucketPrefix(): String = "lumi"

    override fun freeStorage(info: StorageInfo, lowerThreshold: Float) {
        val contentBucket = lumiContentManagementService.getContentBucket()
        if (info.location !== contentBucket) {
            return
        }
        val targetSize = (lowerThreshold * info.maxSize).toLong()
        lumiContentManagementService.freeContentBucket(
            targetSize = targetSize
        )
    }
}