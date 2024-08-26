package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.exception.ResourceNotFoundException
import org.edu_sharing.rendering.service.ContentTransferService
import org.springframework.stereotype.Component

/**
 * Handles storage requests directly without creating asynchronous jobs for adding and converting data.
 * This class stores content as is
 */
@Component
class DirectStorageHandler(
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StorageService
) {

    fun getObjectLinkList(cacheObject: CacheObject): List<ObjectLink> {
        var existingObjectLink = getObjectLink(cacheObject)

        if (existingObjectLink == null) {
            val objectInputStream = contentTransferService.getAsInputStream(cacheObject)
            this.storageImplementation.putObject(cacheObject, objectInputStream)
            existingObjectLink = getObjectLink(cacheObject)
        }
        return listOf(existingObjectLink ?: ObjectLink(link = ""))
    }

    private fun getObjectLink(cacheObject: CacheObject): ObjectLink? {
        return try {
            storageImplementation.getObjectLink(cacheObject)
        } catch (_: ResourceNotFoundException) {
            null
        }
    }
}
