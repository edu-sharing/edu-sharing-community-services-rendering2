package org.edu_sharing.rendering.modules

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.exception.ResourceNotFoundException
import org.edu_sharing.rendering.service.ContentTransferService
import org.springframework.stereotype.Component

@Component
class DefaultStrategy (
    private val contentTransferService: ContentTransferService,
    private val storageImplementation: StorageService
) {
    fun getObjectLinkList(cacheObject: CacheObject): List<ObjectLink> {
        val objectLinkList = mutableListOf<ObjectLink>()
        val existingObjectLink = getObjectLink(cacheObject)
        if (existingObjectLink != null) {
            objectLinkList.add(existingObjectLink)
        } else {
            val objectInputStream = contentTransferService.getAsInputStream(cacheObject)
            this.storageImplementation.putObject(cacheObject, objectInputStream)
            objectLinkList.add(getObjectLink(cacheObject) ?: ObjectLink(link = ""))
        }
        return objectLinkList
    }

    private fun getObjectLink(cacheObject: CacheObject): ObjectLink? {
        return try {
            storageImplementation.getObjectLink(cacheObject)
        } catch (_: ResourceNotFoundException) {
            null
        }
    }
}
