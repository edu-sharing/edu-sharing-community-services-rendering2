package org.edu_sharing.rendering.edusharingRepo

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.core.exception.EntryNotFoundException
import org.edu_sharing.rendering.edusharingRepo.dto.BucketUsageInfo
import org.edu_sharing.rendering.edusharingRepo.dto.StorageUsageInfo
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Admin-Endpoints für die Storage-Übersicht des Dashboards, gescopt auf genau eine repoId.
 *
 * Standardmäßig werden die billigen, repo-genauen Tracking-Aggregationen verwendet
 * ([TrackingService.getBucketAggregation]). Mit `exact=true` wird – nur im
 * Per-Repo-Bucket-Modus, in dem eine S3-Neuberechnung repo-genau möglich ist – live aus S3
 * gerechnet ([StorageService.getUsedSpace]).
 */
@RestController
@RequestMapping("/admin")
@SecurityRequirement(name = "basicAuth")
@ConditionalOnMaster
class AdminStorageController(
    private val trackingService: TrackingService,
    private val storageService: StorageService,
    private val repositoryRegistrationStorageService: RepositoryRegistrationStorageService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/storage/usage")
    fun getStorageUsage(
        @RequestParam repoId: String,
        @RequestParam(required = false, defaultValue = "false") exact: Boolean
    ): StorageUsageInfo {
        log.debug("GET /admin/storage/usage for repoId=$repoId, exact=$exact")

        val quotaRaw = repositoryRegistrationStorageService.getRegistrationByRepoId(repoId)
            .map { it.quota }
            .orElseThrow { EntryNotFoundException("No repository registration found for repoId $repoId.") }

        // Eine exakte S3-Neuberechnung ist nur dann repo-genau, wenn pro Repo getrennte Buckets
        // verwendet werden. Sonst (z.B. byType) würden Objekte anderer Repos mitgezählt – dann
        // bleiben wir bei der repo-genauen Tracking-Aggregation.
        val useExact = exact && storageService.isStoringByRepoId()

        val (totalSize, buckets) = if (useExact) {
            val (size, bucketNames) = storageService.getUsedSpace(repoId)
            size to bucketNames.map { BucketUsageInfo(it, storageService.getDirectorySize(it, "")) }
        } else {
            val aggregation = trackingService.getBucketAggregation().firstOrNull { it.repoId == repoId }
            (aggregation?.totalSize ?: 0L) to
                (aggregation?.buckets?.map { BucketUsageInfo(it.name, it.size) } ?: emptyList())
        }

        val quota = quotaRaw.takeIf { it > 0 }
        val usedPercent = quota?.let { (totalSize.toDouble() / it.toDouble()) * 100.0 }

        return StorageUsageInfo(
            repoId = repoId,
            totalSize = totalSize,
            quota = quota,
            usedPercent = usedPercent,
            exact = useExact,
            buckets = buckets
        )
    }
}
