package org.edu_sharing.rendering.modules.h5p.lumi

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.storage.StorageManager
import org.edu_sharing.rendering.storage.bucket.BucketStrategy
import org.springframework.stereotype.Component

@Component
class LumiStorageManager(
    private val lumiContentManagementService: LumiContentManagementService,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val bucketStrategy: BucketStrategy
): StorageManager {
    private val prefix = "lumi"
    override fun deleteObjectsFromStorage(trackingEntries: List<TrackingEntry>) {
        for (trackingEntry in trackingEntries) {
            lumiContentManagementService.deleteContent(trackingEntry)
        }
    }

    override fun getManagedBuckets(repoId: String): List<String> {
        return listOf(lumiContentManagementService.getContentBucket())
    }

    override fun getByBucketName(
        bucketName: String,
        repoId: String
    ): StorageManager? {
        if (bucketStrategy.isPrefixBased()) {
            return if (bucketName.startsWith(prefix)) this else null
        }
        return if (bucketName == lumiContentManagementService.getContentBucket()) this else null
    }
}
