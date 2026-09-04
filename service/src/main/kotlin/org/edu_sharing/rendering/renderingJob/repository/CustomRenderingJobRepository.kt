package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CustomRenderingJobRepository {
    fun updateStatusWithoutVersion(jobId: ObjectId, status: RenderingJobStatus)

    /**
     * Admin-Job-Liste eines Repos (paginiert), optional nach einer oder mehreren Statuswerten
     * gefiltert (leer/null ⇒ kein Filter) und per Freitext durchsucht (`search`, komma-/
     * leerzeichengetrennte Begriffe, jeder als Regex über module/esObjectId/errorMessage/status,
     * Ergebnis matcht bei Treffer auf irgendeinen Begriff). Optional auf einen Zeitraum
     * eingegrenzt (`createdFrom`/`createdTo`, epoch-ms, inklusiv, gegen `creationTimestamp`).
     * Sortierung kommt über das `Pageable` (vom Controller aus einer Whitelist gebaut).
     */
    fun findJobsPage(
        repoId: String,
        statuses: List<RenderingJobStatus>?,
        search: String?,
        createdFrom: Long?,
        createdTo: Long?,
        pageable: Pageable
    ): Page<RenderingJob>
}