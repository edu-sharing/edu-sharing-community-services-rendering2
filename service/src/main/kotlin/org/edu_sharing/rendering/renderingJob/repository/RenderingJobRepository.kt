package org.edu_sharing.rendering.renderingJob.repository

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface RenderingJobRepository: MongoRepository<RenderingJob, ObjectId>, CustomRenderingJobRepository {
    fun findAllByEsObjectId(id: String): List<RenderingJob>
    fun findByIdAndStatus(id: ObjectId, status: RenderingJobStatus): RenderingJob?

    // Candidates for the QUEUED-reaper safety net: a main job whose job-queue message was itself lost
    // (JobReceiver never ran, so no sub-jobs were ever created) sits in this state forever otherwise.
    // Filtered further (subJobs.isEmpty()) by the caller — the small result set makes resolving the
    // lazy @DocumentReference per candidate cheap.
    fun findByStatusAndLastModifiedDateBefore(status: RenderingJobStatus, cutoff: Instant): List<RenderingJob>

    // Admin-Dashboard: alle Abfragen sind auf genau eine repoId gescopt.
    // (Listing inkl. Suche/Sortierung läuft über CustomRenderingJobRepository.findJobsPage.)
    fun countByRepoId(repoId: String): Long
    fun countByRepoIdAndStatus(repoId: String, status: RenderingJobStatus): Long
}
