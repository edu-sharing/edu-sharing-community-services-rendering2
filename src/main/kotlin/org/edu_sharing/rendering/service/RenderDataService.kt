package org.edu_sharing.rendering.service

import io.minio.errors.ErrorResponseException
import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service

@Service
class RenderDataService (
    private val storageImplementation: StorageService,
    @Qualifier("webApplicationContext") private val resourceLoader: ResourceLoader
) {
    @Value("\${edu_sharing.video_resolutions}")
    lateinit var videoResolutions: List<Int>

    var objectLinkList = mutableListOf<String>()
    var jobIds = mutableListOf<Int>()

    fun getRenderData(request: RenderDataRequest): RenderDataResponse {
        // a) check if object(s) with same hash already exist(s)
        // IF YES: get links -> return stuff (maybe data enrichment)
        // IF NO:
        // - get object type
        //      IF job type: create job(s)
        //      ELSE get objects from repo and cache them, write to db for bookkeeping -> get links -> return
        // push job to appropriate queue
        val cacheObject = CacheObject(
            nodeId = request.nodeId,
            type = request.type,
            hash = request.hash,
            size = request.size,
            mimeType = request.mimeType
        )
        this.compileResponseLists(cacheObject)
        return RenderDataResponse(objectLinkList, jobIds)
    }

    fun compileResponseLists(cacheObject: CacheObject) {
        if (cacheObject.type != "video") {
            this.videoResolutions.forEach {
                cacheObject.quality = it
                val link = this.retrieveObjectLink(cacheObject)
                if (link != null) {
                    this.objectLinkList.add(link)
                } else {
                    this.jobIds.add(this.createJob(cacheObject))
                }
            }
        } else {
            val link = retrieveObjectLink(cacheObject)
            if (link != null) {
                this.objectLinkList.add(link)
            } else {
                this.cacheObjectData(cacheObject)
            }
        }
    }

    private fun retrieveObjectLink(cacheObject: CacheObject): String? {
        try {
            return storageImplementation.getObjectLink(cacheObject)
        } catch (_: ErrorResponseException) {
            return null
        }
    }

    private fun createJob(cacheObject: CacheObject): Int {
        // Make sure to return matching job ids if currently processing
        //      How? check if jobs for the object are currently active
        // 1. Create copy job to tmp bucket and push to copy job queue
        // 2. Consumer of copy job creates conversion jobs
        return 0
    }

    private fun cacheObjectData(cacheObject: CacheObject) {
        val file = resourceLoader.getResource("classpath:lviv.jpg").file
        cacheObject.size = file.length()
        this.storageImplementation.putObject(cacheObject, file.inputStream())
    }
}