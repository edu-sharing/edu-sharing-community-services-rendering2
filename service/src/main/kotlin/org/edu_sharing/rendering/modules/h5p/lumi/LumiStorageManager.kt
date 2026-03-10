package org.edu_sharing.rendering.modules.h5p.lumi

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.storage.StorageManager
import org.springframework.stereotype.Component

@Component
class LumiStorageManager(
    private val lumiContentManagementService: LumiContentManagementService,
): StorageManager {
    override fun deleteObjectsFromStorage(trackingEntries: List<TrackingEntry>) {
        for (trackingEntry in trackingEntries) {
            lumiContentManagementService.deleteContent(trackingEntry)
        }
    }

    override fun getManagedBuckets(repoId: String): List<String> {
        return listOf(lumiContentManagementService.getContentBucket(repoId))
    }

    override fun isBucketOwner(
        bucketName: String,
        repoId: String
    ): Boolean {
        return bucketName == lumiContentManagementService.getContentBucket(repoId);
    }
}
