package org.edu_sharing.rendering.renderingJob

import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException

private val log = LoggerFactory.getLogger("org.edu_sharing.rendering.renderingJob.JobDeduplication")

/**
 * Reuse-or-create guard for the `activeJobPerNodeHash` unique index (see
 * [org.edu_sharing.rendering.renderingJob.entity.RenderingJob]). Returns an already-active job id
 * from [findActiveJobId] if one exists; otherwise runs [create].
 *
 * If [create] loses a concurrent race — a parallel request inserted the active job for the same
 * node+hash first, so the unique index rejects our insert with [DuplicateKeyException] before any
 * queue message is published — the winner's active job id is returned instead of propagating the
 * error. The rethrow guards the (near-impossible) case where the winner already went terminal in
 * the tiny window between the failed insert and the re-lookup.
 *
 * The differing lookup key (node-only vs. node+hash) and creation/enqueue flow stay in each caller;
 * only this find → create → on-duplicate-reuse orchestration is shared.
 */
fun retrieveOrCreateDeduplicatedJob(
    findActiveJobId: () -> String?,
    create: () -> String
): String {
    findActiveJobId()?.let { return it }
    return try {
        create()
    } catch (e: DuplicateKeyException) {
        log.debug("Lost job-creation race (activeJobPerNodeHash); reusing the concurrently created job", e)
        findActiveJobId() ?: throw e
    }
}
