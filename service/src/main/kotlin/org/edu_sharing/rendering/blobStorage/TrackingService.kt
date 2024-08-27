package org.edu_sharing.rendering.blobStorage

import com.mongodb.MongoException
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.entity.TrackingEntry
import org.edu_sharing.rendering.repository.mongo.TrackingEntryRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.data.domain.Page

@Service
class TrackingService(
    protected val trackingEntryRepository: TrackingEntryRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    inner class TrackingIterator(val bucket: String) {
        private var page = PageRequest.of(0, 100, Sort.Direction.ASC, "lastAccessed")
        private var result: Page<TrackingEntry>? = null
        fun getNext() : List<TrackingEntry> {
            result = trackingEntryRepository.findAllByBucket(
                bucket,
                page
            )
            page = result.nextPageable()
            return result!.content
        }

        fun hasNext() : Boolean{
            return result.hasNext()
        }
    }

    fun getOldestTrackedObjects(bucket: String) : TrackingIterator {
        return TrackingIterator(bucket)
    }

    fun trackCacheObject(cacheObject: CacheObject, bucket: String) {
        val trackingEntry = trackingEntryRepository.findByRepoIdAndNodeIdAndHashAndBucket(cacheObject.repoId, cacheObject.nodeId, cacheObject.hash,  bucket)
            .orElse(
                TrackingEntry.of(
                repoId = cacheObject.repoId,
                nodeId = cacheObject.nodeId,
                hash = cacheObject.hash,
                type = cacheObject.type,
                bucket = bucket
            ))
        try {
            trackingEntryRepository.save(trackingEntry)
        } catch (exception: DuplicateKeyException) {
            log.warn("tracking entry for node id ${cacheObject.nodeId} already exists.")
        } catch (exception: MongoException) {
            log.warn("Error creating tracking entry for node id ${cacheObject.nodeId}: ${exception.message}")
        }
    }

    fun deleteTrackedObject(cacheObject: CacheObject, bucket: String){
        trackingEntryRepository.deleteByRepoIdAndNodeIdAndHashAndBucket(cacheObject.repoId, cacheObject.nodeId, cacheObject.hash, bucket)
    }

    fun deleteAllTrackedObjects(trackedObjects : Iterable<TrackingEntry>) {
        trackingEntryRepository.deleteAll(trackedObjects)
    }
}

