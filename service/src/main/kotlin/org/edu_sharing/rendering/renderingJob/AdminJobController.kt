package org.edu_sharing.rendering.renderingJob

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.core.exception.EntryNotFoundException
import org.edu_sharing.rendering.renderingJob.dto.JobListItem
import org.edu_sharing.rendering.renderingJob.dto.JobPage
import org.edu_sharing.rendering.renderingJob.dto.JobStatsInfo
import org.edu_sharing.rendering.renderingJob.dto.SubJobInfo
import org.edu_sharing.rendering.renderingJob.dto.SubJobStats
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Admin-Endpoints für die Job-Verwaltung des Dashboards, gescopt auf genau eine repoId.
 *
 * Hinweis: `RenderingJob` hat einen TTL-Index (6h), daher zeigt die Liste nur jüngere Jobs.
 * Beim Löschen muss die RabbitMQ-Queue nicht angefasst werden – Receiver verwerfen Messages
 * ohne zugehörigen DB-Eintrag automatisch (siehe `JobReceiver`/`*Receiver`).
 */
@RestController
@RequestMapping("/admin")
@SecurityRequirement(name = "basicAuth")
@ConditionalOnMaster
class AdminJobController(
    private val renderingJobRepository: RenderingJobRepository,
    private val subJobRepository: SubJobRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/jobs/stats")
    fun getJobStats(@RequestParam repoId: String): JobStatsInfo {
        log.debug("GET /admin/jobs/stats for repoId=$repoId")
        val subJobCounts = subJobRepository.countSubJobsByStatusForRepo(repoId)
            .associate { it.status to it.count }
        fun sub(status: SubJobStatus) = subJobCounts[status] ?: 0L
        return JobStatsInfo(
            repoId = repoId,
            queued = renderingJobRepository.countByRepoIdAndStatus(repoId, RenderingJobStatus.QUEUED),
            processing = renderingJobRepository.countByRepoIdAndStatus(repoId, RenderingJobStatus.PROCESSING),
            finished = renderingJobRepository.countByRepoIdAndStatus(repoId, RenderingJobStatus.FINISHED),
            failed = renderingJobRepository.countByRepoIdAndStatus(repoId, RenderingJobStatus.FAILED),
            partiallyFailed = renderingJobRepository.countByRepoIdAndStatus(repoId, RenderingJobStatus.PARTIALLY_FAILED),
            total = renderingJobRepository.countByRepoId(repoId),
            subJobs = SubJobStats(
                queued = sub(SubJobStatus.QUEUED),
                processing = sub(SubJobStatus.PROCESSING),
                finished = sub(SubJobStatus.FINISHED),
                failed = sub(SubJobStatus.FAILED),
                total = subJobCounts.values.sum()
            )
        )
    }

    @GetMapping("/jobs")
    fun listJobs(
        @RequestParam repoId: String,
        @RequestParam(required = false) status: RenderingJobStatus?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int
    ): JobPage {
        log.debug("GET /admin/jobs for repoId=$repoId, status=$status, page=$page, size=$size")
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "creationTimestamp"))
        val result: Page<RenderingJob> = if (status != null) {
            renderingJobRepository.findByRepoIdAndStatus(repoId, status, pageable)
        } else {
            renderingJobRepository.findByRepoId(repoId, pageable)
        }
        return JobPage(
            content = result.content.map { toJobListItem(it) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @DeleteMapping("/jobs/{id}")
    fun deleteJob(@PathVariable id: String): ResponseEntity<Void> {
        log.debug("DELETE /admin/jobs/$id")
        if (!ObjectId.isValid(id)) {
            throw EntryNotFoundException("Invalid job id: $id")
        }
        val jobId = ObjectId(id)
        val job = renderingJobRepository.findById(jobId).orElseThrow {
            EntryNotFoundException("No rendering job found for id $id.")
        }
        // SubJobs zuerst löschen (keine kaskadierende DocumentReference), dann den Job.
        subJobRepository.deleteByParentId(job.id)
        renderingJobRepository.delete(job)
        return ResponseEntity.noContent().build()
    }

    private fun toJobListItem(job: RenderingJob): JobListItem {
        val subJobs = subJobRepository.findByParentId(job.id).map {
            SubJobInfo(
                id = it.id.toHexString(),
                routingKey = it.routingKey,
                status = it.status,
                quality = it.quality,
                progress = it.progress,
                errorMessage = it.errorMessage
            )
        }
        return JobListItem(
            id = job.id.toHexString(),
            module = job.module,
            status = job.status,
            esObjectId = job.esObjectId,
            esObjectType = job.esObjectType,
            mimeType = job.mimeType,
            creationTimestamp = job.creationTimestamp,
            finishedTimestamp = job.finishedTimestamp,
            errorMessage = job.errorMessage,
            subJobs = subJobs
        )
    }
}
