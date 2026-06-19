package org.edu_sharing.rendering.storage.bucket

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
@ConditionalOnExternalBucket
class ExternalBucketStrategy(
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService
): BaseBucketStrategy() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getCacheObjectRootPath(cacheObject: CacheObject): String {
        return "${cacheObject.type}/${cacheObject.nodeId}/${cacheObject.hash}"
    }

    override fun getBucket(cacheObject: CacheObject): String {
        val bucket = repositoryRegistrationStorageService
            .getRegistrationByRepoId(cacheObject.repoId)
            .orElseThrow {
                IllegalArgumentException("Unknown repository identifier ${cacheObject.repoId} provided")
            }
            .buckets
            ?.renderingBucket
            ?: throw IllegalArgumentException("No bucket ID configured for repository ${cacheObject.repoId}")
        log.debug("Resolved bucket (external strategy): repoId=${cacheObject.repoId}, bucket=$bucket")
        return bucket
    }

    override fun prefixStaticPath(
        cacheObject: CacheObject,
        path: String
    ): String {
        val storagePath = getStoragePath(cacheObject, path)
        return "/${cacheObject.repoId}/${storagePath.trimStart('/')}"
    }

    override fun getCacheObjectFromStoragePath(
        bucket: String,
        storagePath: String
    ): CacheObject? {
        val (type, nodeId, hash) = storagePath.trimStart('/').split("/", limit = 3)
        return CacheObject.of(repoId = bucket, type = type, nodeId = nodeId, hash = hash)
    }

    override fun isManagedBucket(bucket: String, repoId: String): Boolean {
        return repositoryRegistrationStorageService
            .getRegistrationByRepoId(repoId)
            .orElse(null)
            ?.buckets
            ?.renderingBucket
            ?.let { bucket == it }
            ?: false
    }

    override fun getTempBucket(repoId: String): String {
        val bucket = repositoryRegistrationStorageService
            .getRegistrationByRepoId(repoId)
            .orElseThrow {
                IllegalArgumentException("Unknown repository identifier $repoId provided")
            }
            .buckets
            ?.tempBucket
            ?: throw IllegalArgumentException("No temp bucket ID configured for repository $repoId")
        log.debug("Resolved temp bucket (external strategy): repoId=$repoId, bucket=$bucket")
        return bucket
    }
}
