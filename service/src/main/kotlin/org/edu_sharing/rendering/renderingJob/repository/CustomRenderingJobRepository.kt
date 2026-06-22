package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CustomRenderingJobRepository {
    fun updateStatusWithoutVersion(jobId: ObjectId, status: RenderingJobStatus)

    /**
     * Admin-Job-Liste eines Repos (paginiert), optional nach Status gefiltert und per Freitext
     * durchsucht (`search`, Regex über module/esObjectId/errorMessage/status). Sortierung kommt
     * über das `Pageable` (vom Controller aus einer Whitelist gebaut).
     */
    fun findJobsPage(
        repoId: String,
        status: RenderingJobStatus?,
        search: String?,
        pageable: Pageable
    ): Page<RenderingJob>
}