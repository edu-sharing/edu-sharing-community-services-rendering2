package org.edu_sharing.rendering.storage

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.springframework.stereotype.Component

@Component
class Rs2StorageManager(
    private val storageService: StorageService,
    private val mapper: Mapper
): StorageManager {
    override fun bucketPrefix(): String = "rs2"
    override fun deleteObjectsFromStorage(trackingEntries: List<TrackingEntry>) {
        val cacheObjects = trackingEntries.map { mapper.trackingEntryToCacheObject(it) }
        storageService.removeObjects(cacheObjects)
    }

    override fun getManagedBuckets(): List<String> {
        return storageService.getBuckets().filter { it.startsWith(bucketPrefix()) }
    }
}
