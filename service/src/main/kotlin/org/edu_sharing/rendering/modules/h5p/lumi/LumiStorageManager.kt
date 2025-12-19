package org.edu_sharing.rendering.modules.h5p.lumi

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.storage.StorageManager
import org.springframework.stereotype.Component

@Component
class LumiStorageManager(
    private val lumiContentManagementService: LumiContentManagementService,
): StorageManager {
    override fun bucketPrefix(): String = "lumi"
    override fun deleteObjectsFromStorage(trackingEntries: List<TrackingEntry>) {
        for (trackingEntry in trackingEntries) {
            lumiContentManagementService.deleteContent(trackingEntry)
        }
    }

    override fun getManagedBuckets() = listOf(lumiContentManagementService.getContentBucket())
}
