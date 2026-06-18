package org.edu_sharing.rendering.renderingJob.dto

import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus

/**
 * Job-Kennzahlen eines Repos für die Dashboard-Kacheln.
 * "Warteschlange" = queued + processing, "Fehlgeschlagen" = failed + partiallyFailed.
 */
data class JobStatsInfo(
    val repoId: String,
    val queued: Long,
    val processing: Long,
    val finished: Long,
    val failed: Long,
    val partiallyFailed: Long,
    val total: Long,
    val subJobs: SubJobStats
)

/** Sub-Job-Kennzahlen eines Repos (über alle Jobs aggregiert). */
data class SubJobStats(
    val queued: Long,
    val processing: Long,
    val finished: Long,
    val failed: Long,
    val total: Long
)

data class SubJobInfo(
    val id: String,
    val routingKey: String,
    val status: SubJobStatus,
    val quality: Int,
    val progress: Int,
    val errorMessage: String?
)

data class JobListItem(
    val id: String,
    val module: String,
    val status: RenderingJobStatus,
    val esObjectId: String,
    val esObjectType: String,
    val mimeType: String,
    val creationTimestamp: Long,
    val finishedTimestamp: Long?,
    val errorMessage: String?,
    val subJobs: List<SubJobInfo>
)

/**
 * Paginierte Job-Liste eines Repos (eigener Typ statt Spring `Page`, damit der OpenAPI-Contract
 * stabil und für die Angular-Codegen sauber bleibt).
 */
data class JobPage(
    val content: List<JobListItem>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
