package org.edu_sharing.rendering.cacheCleaner

import com.mongodb.MongoException
import org.edu_sharing.rendering.core.dto.CacheObject
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.util.*
import java.util.function.Function

@Service
class TrackingService(
    private val trackingEntryRepository: TrackingEntryRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Iterator used for traversing through pages of `TrackingEntry` objects retrieved from a data source.
     * This class uses a delegate function to fetch paginated data based on a `Pageable` object.
     *
     * The returned items are ordered oldest to newest (last accessed)
     *
     * @property delegate A function that takes a `Pageable` object and returns a `Page` of `TrackingEntry` instances.
     */
    class TrackingIterator(val delegate: Function<Pageable,Page<TrackingEntry>>) : Iterator<TrackingEntry> {
        private var page: Pageable = PageRequest.of(0, 100, Sort.Direction.ASC, "lastAccessed")
        private lateinit var result: Page<TrackingEntry>
        private var currentIndex = 0

        init {
            fetchNext()
        }

        private fun fetchNext() {
            result = delegate.apply(page)
            page = result.nextPageable()
            currentIndex = 0
        }

        fun hasNext(): Boolean {
            return result?.hasNext() != false
        }
    }

    fun getTrackedObjectsByBucket(bucket: String): TrackingIterator {
        return TrackingIterator {trackingEntryRepository.findAllByBucket(bucket, it)}
    }

    /**
     * Retrieves a `TrackingIterator` for iterating through tracked objects associated with the specified repository ID.
     *
     * The iterator orders tracked objects by the last accessed date (oldest to newest).
     *
     * @param repoId The ID of the repository whose tracked objects are to be retrieved.
     * @return A `TrackingIterator` instance for traversing pages of tracked objects linked to the given repository ID.
     */
    fun getTrackedObjectsByRepoId(repoId: String): TrackingIterator {
        return TrackingIterator {trackingEntryRepository.findAllByRepoId(repoId, it)}
    }

    fun trackCacheObject(cacheObject: CacheObject, bucket: String, size: Long? = null) {
        val trackingEntry = trackingEntryRepository.findByRepoIdAndNodeIdAndHashAndBucket(
            cacheObject.repoId,
            cacheObject.nodeId,
            cacheObject.hash,
            bucket
        )
            .orElse(
                TrackingEntry.of(
                    repoId = cacheObject.repoId,
                    nodeId = cacheObject.nodeId,
                    hash = cacheObject.hash,
                    type = cacheObject.type,
                    bucket = bucket,
                    binarySize = cacheObject.size
                )
            )
        try {
            trackingEntry.lastAccessed = Date()
            if (size != null) {
                trackingEntry.binarySize = size
            }
            trackingEntryRepository.save(trackingEntry)
        } catch (_: DuplicateKeyException) {
            log.warn("tracking entry for node id ${cacheObject.nodeId} already exists.")
        } catch (exception: MongoException) {
            log.warn("Error creating tracking entry for node id ${cacheObject.nodeId}: ${exception.message}")
        }
    }

    fun deleteTrackedObject(cacheObject: CacheObject, bucket: String) {
        trackingEntryRepository.deleteByRepoIdAndNodeIdAndHashAndBucket(
            cacheObject.repoId,
            cacheObject.nodeId,
            cacheObject.hash,
            bucket
        )
    }

    fun deleteAllTrackedObjects(trackedObjects: Iterable<TrackingEntry>) {
        trackingEntryRepository.deleteAll(trackedObjects)
    }

    fun getBucketAggregation(): List<BucketAggregation> {
        return trackingEntryRepository.getBucketAggregation()
    }
}

