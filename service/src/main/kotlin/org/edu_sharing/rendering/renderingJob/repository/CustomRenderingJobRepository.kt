package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CustomRenderingJobRepository {
    fun updateStatusWithoutVersion(jobId: ObjectId, status: RenderingJobStatus)

    /**
     * Sets the message the client shows for a failed job, without a version check.
     *
     * A receiver cannot `save()` the job again for this: [RenderingJob.version] is a `val`, so `save()` returns a new
     * instance and leaves the receiver's own at the old version - saving that one fails with an optimistic locking
     * error. A targeted `$set`, like [updateStatusWithoutVersion], cannot.
     */
    fun updateErrorMessageWithoutVersion(jobId: ObjectId, errorMessage: String)

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