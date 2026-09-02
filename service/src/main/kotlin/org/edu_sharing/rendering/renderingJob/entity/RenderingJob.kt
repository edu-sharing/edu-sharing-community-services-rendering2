package org.edu_sharing.rendering.renderingJob.entity

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.ReadOnlyProperty
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference
import java.time.Instant

@Document
@CompoundIndexes(
    // At most one active (QUEUED/PROCESSING) job per node+hash, so two concurrent renders of
    // the same node can't both slip past the check-then-create in the dedup creators and
    // insert duplicates. Partial, so terminal jobs leave the index (a re-render of the same
    // node+hash is allowed once the old job finished/failed). Only jobs flagged [deduplicated]
    // participate: modules that create a fresh job per request by design (Sodix, Onyx) leave the
    // flag false and are exempt, and legacy jobs written before this field existed are also
    // excluded (so the unique index can build over data that may still hold duplicates).
    // Mongo partial indexes forbid $ne/$nin/$not — hence an opt-in equality flag, not a module
    // exclusion. Status is stored as the enum name — match the string form.
    CompoundIndex(
        name = "activeJobPerNodeHash",
        def = "{'esObjectId': 1, 'esHash': 1}",
        unique = true,
        partialFilter = "{ 'status': { '\$in': ['QUEUED', 'PROCESSING'] }, 'deduplicated': true }"
    ),
    // Trägt GET /admin/jobs (AdminJobController.listJobs). 'status' bewusst NACH
    // creationTimestamp: der häufigste Fall ist der Default-Aufruf ohne Status-Filter (jeder
    // Poll-Tick des Dashboards, siehe jobs.ts) sortiert nach creationTimestamp – die Reihenfolge
    // liefert dafür die Sortierung direkt aus dem Index (kein In-Memory-Sort). Mit Status-Filter
    // nutzt Mongo weiterhin den repoId-Präfix zum Scannen und filtert status nachträglich; da die
    // Collection per TTL auf 8 Tage begrenzt ist, ist das unkritisch. Deckt auch
    // countByRepoId(AndStatus) (Stats-Endpoint) über denselben repoId-Präfix ab.
    CompoundIndex(
        name = "repoIdCreationTimestampStatus",
        def = "{'repoId': 1, 'creationTimestamp': -1, 'status': 1}"
    )
)
data class RenderingJob(
    @Id
    val id: ObjectId = ObjectId(),
    @Indexed
    var status: RenderingJobStatus = RenderingJobStatus.QUEUED,
    var module: String,
    val esObjectType: String,
    val esObjectId: String,
    val repoId: String,
    val esHash: String,
    val mimeType: String,
    val nodeVersion: String,
    val size: Long? = null,
    val creationTimestamp: Long = System.currentTimeMillis(),
    var finishedTimestamp: Long? = null,
    @ReadOnlyProperty
    @DocumentReference(lazy = true, lookup = "{'parent':?#{#self._id} }")
    var subJobs: MutableList<SubJob> = ArrayList(),
    @Indexed(expireAfter = "8d")
    @LastModifiedDate
    var lastModifiedDate: Instant? = null,
    @Version
    val version: Int? = null,
    val conversionType: Boolean = false,
    val externalUrl: String? = null,
    var errorMessage: String? = null,
    // Opt-in flag for the activeJobPerNodeHash unique index: set by the dedup creators
    // (MainJobCreationService / H5pJobService / EduHtmlService) so at most one active job per
    // node+hash can exist. Modules that create a fresh job per request (Sodix, Onyx) leave it false.
    var deduplicated: Boolean = false
)
