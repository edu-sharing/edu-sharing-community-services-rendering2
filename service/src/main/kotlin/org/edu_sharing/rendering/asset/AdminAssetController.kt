package org.edu_sharing.rendering.asset

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.edu_sharing.rendering.asset.dto.AssetDeletionResult
import org.edu_sharing.rendering.asset.dto.AssetInfo
import org.edu_sharing.rendering.asset.dto.AssetNode
import org.edu_sharing.rendering.asset.dto.AssetNodePage
import org.edu_sharing.rendering.asset.dto.AssetPage
import org.edu_sharing.rendering.asset.dto.AssetTypeInfo
import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.cacheCleaner.TrackingEntryRepository
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.EntryNotFoundException
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Admin-Endpoints für die S3-Asset-Verwaltung des Dashboards, gescopt auf genau eine repoId.
 *
 * Die Übersicht ist **Tracking-getrieben**: Im Default-Bucket-Modus `byType` enthalten die
 * S3-Keys keine repoId, eine repo-genaue Filterung ist nur über die `Tracking`-Collection
 * möglich (dort sind `repoId`, `nodeId`, `hash`, `type`, `bucket`, `binarySize` hinterlegt).
 * Löschungen entfernen S3-Objekt **und** Tracking-Eintrag, damit Tracking-Größen und reale
 * Belegung konsistent bleiben (Muster: `AdminController.deleteObjectFromCache`).
 */
@RestController
@RequestMapping("/admin")
@SecurityRequirement(name = "basicAuth")
@Tag(name = "assets")
@ConditionalOnMaster
class AdminAssetController(
    private val trackingEntryRepository: TrackingEntryRepository,
    private val storageService: StorageService,
    private val mapper: Mapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val DELETE_BATCH_SIZE = 1000
    }

    @GetMapping("/assets")
    fun listAssets(
        @RequestParam repoId: String,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int
    ): AssetPage {
        log.debug("GET /admin/assets for repoId=$repoId, type=$type, page=$page, size=$size")
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastAccessed"))
        val result: Page<TrackingEntry> = if (type != null) {
            trackingEntryRepository.findAllByRepoIdAndType(repoId, type, pageable)
        } else {
            trackingEntryRepository.findAllByRepoId(repoId, pageable)
        }
        return AssetPage(
            content = result.content.map { toAssetInfo(it) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @GetMapping("/assets/nodes")
    fun listAssetNodes(
        @RequestParam repoId: String,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false, defaultValue = "desc") dir: String,
        @RequestParam(required = false) accessedFrom: Long?,
        @RequestParam(required = false) accessedTo: Long?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int
    ): AssetNodePage {
        log.debug("GET /admin/assets/nodes for repoId=$repoId, type=$type, search=$search, sort=$sort, dir=$dir, accessedFrom=$accessedFrom, accessedTo=$accessedTo, page=$page, size=$size")
        val result = trackingEntryRepository.aggregateNodes(repoId, type, search, sort, dir, accessedFrom, accessedTo, page, size)
        val totalPages = if (size > 0) ((result.total + size - 1) / size).toInt() else 0
        return AssetNodePage(
            content = result.content.map {
                AssetNode(
                    nodeId = it.nodeId,
                    type = it.type,
                    bucket = it.bucket,
                    hash = it.hash,
                    size = it.size,
                    lastAccessed = it.lastAccessed,
                    versionCount = it.versionCount,
                    totalSize = it.totalSize
                )
            },
            page = page,
            size = size,
            totalElements = result.total,
            totalPages = totalPages
        )
    }

    @GetMapping("/assets/versions")
    fun listAssetVersions(
        @RequestParam repoId: String,
        @RequestParam nodeId: String
    ): List<AssetInfo> {
        log.debug("GET /admin/assets/versions for repoId=$repoId, nodeId=$nodeId")
        return trackingEntryRepository.findAllByRepoIdAndNodeId(repoId, nodeId)
            .sortedByDescending { it.lastAccessed }
            .map { toAssetInfo(it) }
    }

    @GetMapping("/assets/types")
    fun listAssetTypes(
        @RequestParam repoId: String,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false, defaultValue = "desc") dir: String
    ): List<AssetTypeInfo> {
        log.debug("GET /admin/assets/types for repoId=$repoId, search=$search, sort=$sort, dir=$dir")
        return trackingEntryRepository.aggregateTypes(repoId, search, sort, dir)
            .map { AssetTypeInfo(type = it.type, count = it.count, totalSize = it.totalSize) }
    }

    /**
     * Löscht ein einzelnes Asset (mit `hash`) oder – wenn `hash` weggelassen wird – alle
     * Versionen einer nodeId.
     */
    @DeleteMapping("/assets")
    fun deleteAsset(
        @RequestParam repoId: String,
        @RequestParam nodeId: String,
        @RequestParam(required = false) hash: String?
    ): AssetDeletionResult {
        log.debug("DELETE /admin/assets for repoId=$repoId, nodeId=$nodeId, hash=$hash")
        val entries = if (hash != null) {
            listOf(
                trackingEntryRepository.findByRepoIdAndNodeIdAndHash(repoId, nodeId, hash).orElseThrow {
                    EntryNotFoundException("No tracking entry found for repoId $repoId, nodeId $nodeId and hash $hash.")
                }
            )
        } else {
            trackingEntryRepository.findAllByRepoIdAndNodeId(repoId, nodeId).ifEmpty {
                throw EntryNotFoundException("No tracking entries found for repoId $repoId and nodeId $nodeId.")
            }
        }
        deleteEntries(entries)
        return AssetDeletionResult(deleted = entries.size.toLong())
    }

    @DeleteMapping("/assets/by-type")
    fun deleteAssetsByType(
        @RequestParam repoId: String,
        @RequestParam type: String
    ): AssetDeletionResult {
        log.debug("DELETE /admin/assets/by-type for repoId=$repoId, type=$type")
        val entries = trackingEntryRepository.findAllByRepoIdAndType(repoId, type)
        deleteEntries(entries)
        return AssetDeletionResult(deleted = entries.size.toLong())
    }

    @DeleteMapping("/assets/all")
    fun deleteAllAssets(@RequestParam repoId: String): AssetDeletionResult {
        log.debug("DELETE /admin/assets/all for repoId=$repoId")
        var deleted = 0L
        // "Drain the first page" – wir holen wiederholt die ersten N Einträge und löschen sie,
        // statt mit wachsendem Offset zu paginieren (das würde durch das Löschen verrutschen).
        while (true) {
            val batch = trackingEntryRepository
                .findAllByRepoId(repoId, PageRequest.of(0, DELETE_BATCH_SIZE))
                .content
            if (batch.isEmpty()) break
            deleteEntries(batch)
            deleted += batch.size
        }
        return AssetDeletionResult(deleted = deleted)
    }

    /**
     * Entfernt die S3-Objekte der übergebenen Tracking-Einträge und anschließend die
     * Tracking-Einträge selbst. `removeObjects` gruppiert nach Bucket und löscht in 1000er-Chunks.
     */
    private fun deleteEntries(entries: List<TrackingEntry>) {
        if (entries.isEmpty()) return
        storageService.removeObjects(entries.map { mapper.trackingEntryToCacheObject(it) })
        trackingEntryRepository.deleteAll(entries)
    }

    private fun toAssetInfo(entry: TrackingEntry): AssetInfo {
        return AssetInfo(
            nodeId = entry.nodeId,
            hash = entry.hash,
            type = entry.type,
            bucket = entry.bucket,
            size = entry.binarySize,
            lastAccessed = entry.lastAccessed.time
        )
    }
}
