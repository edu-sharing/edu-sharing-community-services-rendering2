package org.edu_sharing.rendering.integration

import org.edu_sharing.rendering.cacheCleaner.TrackingEntry
import org.edu_sharing.rendering.cacheCleaner.TrackingEntryRepository
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.edusharingRepo.entity.ExternalBucket
import org.edu_sharing.rendering.edusharingRepo.entity.ExternalBuckets
import org.edu_sharing.rendering.edusharingRepo.entity.ModuleSettings
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.storage.StorageService
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64

/**
 * Integrationstests für die neuen Admin-Endpoints (Storage, Jobs, Repo-Details, Assets).
 *
 * Läuft in der Master-Rolle (aus den Test-Properties) mit aktivierter Security; alle Aufrufe
 * sind via HTTP-Basic (admin/admin, siehe `application.properties` der Tests) authentifiziert.
 * Daten werden direkt über die Repositories/den StorageService gesät. Die Asset-Löschpfade
 * werden end-to-end gegen den MinIO-Testcontainer geprüft.
 */
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminEndpointsIntegrationTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val jobRepository: RenderingJobRepository,
    @param:Autowired private val subJobRepository: SubJobRepository,
    @param:Autowired private val trackingEntryRepository: TrackingEntryRepository,
    @param:Autowired private val registrationStorageService: RepositoryRegistrationStorageService,
    @param:Autowired private val storageService: StorageService,
) : AbstractIntegrationTest() {

    private val basicAuth = "Basic " + Base64.getEncoder().encodeToString("admin:admin".toByteArray())

    @BeforeEach
    fun cleanCollections() {
        jobRepository.deleteAll()
        subJobRepository.deleteAll()
        trackingEntryRepository.deleteAll()
    }

    // --- Auth ---------------------------------------------------------------------------------

    @Test
    fun `admin endpoint rejects unauthenticated request`() {
        mockMvc.perform(get("/admin/jobs/stats").param("repoId", "any"))
            .andExpect(status().isUnauthorized)
    }

    // --- Storage ------------------------------------------------------------------------------

    @Test
    fun `storage usage aggregates per repo with quota percentage`() {
        val repoId = "storage-repo-quota"
        storeRegistration(repoId, quota = 1000)
        trackingEntryRepository.save(trackingEntry(repoId, "n1", "h1", "image", "rs2-image", 300))
        trackingEntryRepository.save(trackingEntry(repoId, "n2", "h2", "video", "rs2-video", 200))

        mockMvc.perform(get("/admin/storage/usage").param("repoId", repoId).header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.repoId").value(repoId))
            .andExpect(jsonPath("$.totalSize").value(500))
            .andExpect(jsonPath("$.quota").value(1000))
            .andExpect(jsonPath("$.usedPercent").value(50.0))
            .andExpect(jsonPath("$.exact").value(false))
            .andExpect(jsonPath("$.buckets.length()").value(2))
            .andExpect(jsonPath("$.buckets[*].name", containsInAnyOrder("rs2-image", "rs2-video")))
    }

    @Test
    fun `storage usage without quota omits percentage`() {
        val repoId = "storage-repo-noquota"
        storeRegistration(repoId, quota = 0)
        trackingEntryRepository.save(trackingEntry(repoId, "n1", "h1", "image", "rs2-image", 42))

        mockMvc.perform(get("/admin/storage/usage").param("repoId", repoId).header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalSize").value(42))
            .andExpect(jsonPath("$.quota", nullValue()))
            .andExpect(jsonPath("$.usedPercent", nullValue()))
    }

    @Test
    fun `storage usage with a bucket quota reports it per bucket and enforced`() {
        val repoId = "storage-repo-bucket-quota"
        storeRegistration(
            repoId,
            quota = 999_999, // repo-wide fallback — must be superseded once a bucket quota exists
            buckets = ExternalBuckets(renderingBucket = ExternalBucket(name = "rendering2", quota = 1000)),
        )
        trackingEntryRepository.save(trackingEntry(repoId, "n1", "h1", "image", "rendering2", 300))
        // A tracked bucket without its own quota (e.g. lumi) stays visible, but without a quota/unenforced.
        trackingEntryRepository.save(trackingEntry(repoId, "n2", "h2", "h5p", "lumi-contentbucket", 50))

        mockMvc.perform(get("/admin/storage/usage").param("repoId", repoId).header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.buckets.length()").value(2))
            .andExpect(jsonPath("$.buckets[?(@.name=='rendering2')].quota").value(1000))
            .andExpect(jsonPath("$.buckets[?(@.name=='rendering2')].usedPercent").value(30.0))
            .andExpect(jsonPath("$.buckets[?(@.name=='rendering2')].enforced").value(true))
            // JsonPath filter expressions always return a list, even with exactly one match — against
            // a matcher (instead of a scalar value) Spring checks the unwrapped result as-is.
            .andExpect(jsonPath("$.buckets[?(@.name=='lumi-contentbucket')].quota").value(contains(nullValue())))
            .andExpect(jsonPath("$.buckets[?(@.name=='lumi-contentbucket')].enforced").value(false))
    }

    @Test
    fun `storage usage for unknown repo returns 404`() {
        mockMvc.perform(get("/admin/storage/usage").param("repoId", "does-not-exist").header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isNotFound)
    }

    // --- Jobs ---------------------------------------------------------------------------------

    @Test
    fun `job stats count by status for the repo`() {
        val repoId = "jobs-repo-stats"
        saveJob(repoId, RenderingJobStatus.QUEUED)
        saveJob(repoId, RenderingJobStatus.QUEUED)
        saveJob(repoId, RenderingJobStatus.PROCESSING)
        saveJob(repoId, RenderingJobStatus.FAILED)
        saveJob(repoId, RenderingJobStatus.PARTIALLY_FAILED)
        // anderes Repo darf die Zählung nicht beeinflussen
        saveJob("other-repo", RenderingJobStatus.QUEUED)

        mockMvc.perform(get("/admin/jobs/stats").param("repoId", repoId).header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.queued").value(2))
            .andExpect(jsonPath("$.processing").value(1))
            .andExpect(jsonPath("$.failed").value(1))
            .andExpect(jsonPath("$.partiallyFailed").value(1))
            .andExpect(jsonPath("$.total").value(5))
    }

    @Test
    fun `list jobs filters by status`() {
        val repoId = "jobs-repo-list"
        saveJob(repoId, RenderingJobStatus.FINISHED)
        saveJob(repoId, RenderingJobStatus.FAILED)
        saveJob(repoId, RenderingJobStatus.FAILED)

        mockMvc.perform(
            get("/admin/jobs")
                .param("repoId", repoId)
                .param("statuses", "FAILED")
                .header(HttpHeaders.AUTHORIZATION, basicAuth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[*].status", containsInAnyOrder("FAILED", "FAILED")))
    }

    @Test
    fun `list jobs filters by multiple statuses`() {
        val repoId = "jobs-repo-list-multi"
        saveJob(repoId, RenderingJobStatus.FINISHED)
        saveJob(repoId, RenderingJobStatus.FAILED)
        saveJob(repoId, RenderingJobStatus.QUEUED)

        mockMvc.perform(
            get("/admin/jobs")
                .param("repoId", repoId)
                .param("statuses", "FAILED", "QUEUED")
                .header(HttpHeaders.AUTHORIZATION, basicAuth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].status", containsInAnyOrder("FAILED", "QUEUED")))
    }

    @Test
    fun `list jobs search accepts comma or space separated multi values`() {
        val repoId = "jobs-repo-search-multi"
        val a = saveJob(repoId, RenderingJobStatus.FINISHED)
        val b = saveJob(repoId, RenderingJobStatus.FINISHED)
        saveJob(repoId, RenderingJobStatus.FINISHED)

        mockMvc.perform(
            get("/admin/jobs")
                .param("repoId", repoId)
                .param("search", "${a.esObjectId}, ${b.esObjectId}")
                .header(HttpHeaders.AUTHORIZATION, basicAuth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].esObjectId", containsInAnyOrder(a.esObjectId, b.esObjectId)))

        mockMvc.perform(
            get("/admin/jobs")
                .param("repoId", repoId)
                .param("search", "${a.esObjectId} ${b.esObjectId}")
                .header(HttpHeaders.AUTHORIZATION, basicAuth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    fun `delete job removes job and its sub jobs`() {
        val repoId = "jobs-repo-delete"
        val job = saveJob(repoId, RenderingJobStatus.FAILED)
        subJobRepository.save(SubJob(routingKey = "image_job", parent = job, status = SubJobStatus.FAILED))
        subJobRepository.save(SubJob(routingKey = "image_job", parent = job, status = SubJobStatus.FINISHED))

        mockMvc.perform(delete("/admin/jobs/${job.id.toHexString()}").header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isNoContent)

        assert(jobRepository.findById(job.id).isEmpty) { "job should be deleted" }
        assert(subJobRepository.findByParentId(job.id).isEmpty()) { "sub jobs should be deleted" }
    }

    @Test
    fun `delete job with invalid id returns 404`() {
        mockMvc.perform(delete("/admin/jobs/not-an-object-id").header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isNotFound)
    }

    // --- Repository details -------------------------------------------------------------------

    @Test
    fun `repository details expose properties without leaking secrets`() {
        val repoId = "details-repo"
        val longKey = "X".repeat(80)
        val registration = RepositoryRegistration(
            repoId = repoId,
            url = "https://repo.example.org/edu-sharing",
            publicKey = longKey,
            domains = listOf("repo.example.org"),
            optionalModules = mutableListOf("h5p"),
            module = mutableMapOf("sodix" to ModuleSettings(credentials = mapOf("apiKey" to "super-secret"), cspHeader = "frame-ancestors *")),
            quota = 12345,
            buckets = ExternalBuckets(
                renderingBucket = ExternalBucket(name = "rb", quota = 999),
                tempBucket = ExternalBucket(name = "tb", quota = 111),
            ),
            signingAlgorithm = "SHA256withRSA",
        )
        registrationStorageService.storeRegistration(registration)

        mockMvc.perform(get("/admin/repository/details").param("repoId", repoId).header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.repoId").value(repoId))
            .andExpect(jsonPath("$.quota").value(12345))
            .andExpect(jsonPath("$.optionalModules", hasItem("h5p")))
            .andExpect(jsonPath("$.renderingBucket").value("rb"))
            .andExpect(jsonPath("$.renderingBucketQuota").value(999))
            .andExpect(jsonPath("$.tempBucket").value("tb"))
            .andExpect(jsonPath("$.tempBucketQuota").value(111))
            // lumi is unreachable in the test context -> content bucket info stays null instead of
            // failing the request.
            .andExpect(jsonPath("$.contentBucket", nullValue()))
            .andExpect(jsonPath("$.contentBucketQuota", nullValue()))
            .andExpect(jsonPath("$.signingAlgorithm").value("SHA256withRSA"))
            // Credential-Schlüssel sind sichtbar, der Wert NICHT.
            .andExpect(jsonPath("$.modules.sodix.credentialKeys", hasItem("apiKey")))
            .andExpect(jsonPath("$.modules.sodix.cspHeader").value("frame-ancestors *"))
            // Public Key nur als gekürzte Vorschau (kein voller 80-Zeichen-Key).
            .andExpect(jsonPath("$.publicKeyPreview", not(longKey)))
    }

    @Test
    fun `repository details for unknown repo returns 404`() {
        mockMvc.perform(get("/admin/repository/details").param("repoId", "nope").header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isNotFound)
    }

    // --- Assets -------------------------------------------------------------------------------

    @Test
    fun `list assets and aggregate types from tracking`() {
        val repoId = "assets-repo-list"
        trackingEntryRepository.save(trackingEntry(repoId, "n1", "h1", "image", "rs2-image", 100))
        trackingEntryRepository.save(trackingEntry(repoId, "n2", "h2", "image", "rs2-image", 50))
        trackingEntryRepository.save(trackingEntry(repoId, "n3", "h3", "video", "rs2-video", 200))

        mockMvc.perform(get("/admin/assets").param("repoId", repoId).header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(3))

        mockMvc.perform(get("/admin/assets").param("repoId", repoId).param("type", "image").header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))

        mockMvc.perform(get("/admin/assets/types").param("repoId", repoId).header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].type", containsInAnyOrder("image", "video")))
    }

    @Test
    fun `delete single asset removes object and tracking entry`() {
        val repoId = "assets-repo-single"
        putAsset(repoId, "n1", "h1", "image", "image/jpeg")
        // putObject legt den Tracking-Eintrag selbst an
        assert(trackingEntryRepository.findByRepoIdAndNodeIdAndHash(repoId, "n1", "h1").isPresent)

        mockMvc.perform(
            delete("/admin/assets")
                .param("repoId", repoId).param("nodeId", "n1").param("hash", "h1")
                .header(HttpHeaders.AUTHORIZATION, basicAuth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deleted").value(1))

        assert(trackingEntryRepository.findByRepoIdAndNodeIdAndHash(repoId, "n1", "h1").isEmpty) { "tracking entry should be gone" }
        assert(!storageService.objectExists(cacheObject(repoId, "n1", "h1", "image", "image/jpeg"))) { "S3 object should be gone" }
    }

    @Test
    fun `list asset nodes bundles versions by nodeId`() {
        val repoId = "assets-repo-nodes"
        // zwei Versionen derselben nodeId + eine andere nodeId
        trackingEntryRepository.save(trackingEntry(repoId, "node-a", "h1", "image", "rs2-image", 100))
        trackingEntryRepository.save(trackingEntry(repoId, "node-a", "h2", "image", "rs2-image", 150))
        trackingEntryRepository.save(trackingEntry(repoId, "node-b", "h3", "video", "rs2-video", 200))

        mockMvc.perform(get("/admin/assets/nodes").param("repoId", repoId).header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content.length()").value(2))

        mockMvc.perform(get("/admin/assets/versions").param("repoId", repoId).param("nodeId", "node-a").header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `list asset nodes filters by multiple types`() {
        val repoId = "assets-repo-nodes-multi-type"
        trackingEntryRepository.save(trackingEntry(repoId, "node-a", "h1", "image", "rs2-image", 100))
        trackingEntryRepository.save(trackingEntry(repoId, "node-b", "h2", "video", "rs2-video", 200))
        trackingEntryRepository.save(trackingEntry(repoId, "node-c", "h3", "audio", "rs2-audio", 50))

        mockMvc.perform(
            get("/admin/assets/nodes")
                .param("repoId", repoId)
                .param("types", "image", "video")
                .header(HttpHeaders.AUTHORIZATION, basicAuth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].type", containsInAnyOrder("image", "video")))
    }

    @Test
    fun `list asset nodes search accepts comma or space separated multi values`() {
        val repoId = "assets-repo-nodes-search-multi"
        trackingEntryRepository.save(trackingEntry(repoId, "node-a", "h1", "image", "rs2-image", 100))
        trackingEntryRepository.save(trackingEntry(repoId, "node-b", "h2", "video", "rs2-video", 200))
        trackingEntryRepository.save(trackingEntry(repoId, "node-c", "h3", "audio", "rs2-audio", 50))

        mockMvc.perform(
            get("/admin/assets/nodes")
                .param("repoId", repoId)
                .param("search", "node-a, node-b")
                .header(HttpHeaders.AUTHORIZATION, basicAuth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].nodeId", containsInAnyOrder("node-a", "node-b")))

        mockMvc.perform(
            get("/admin/assets/nodes")
                .param("repoId", repoId)
                .param("search", "node-a node-b")
                .header(HttpHeaders.AUTHORIZATION, basicAuth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    fun `delete assets by type removes only that type`() {
        val repoId = "assets-repo-bytype"
        putAsset(repoId, "n1", "h1", "image", "image/jpeg")
        putAsset(repoId, "n2", "h2", "video", "video/mp4")

        mockMvc.perform(
            delete("/admin/assets/by-type")
                .param("repoId", repoId).param("type", "image")
                .header(HttpHeaders.AUTHORIZATION, basicAuth),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deleted").value(1))

        assert(trackingEntryRepository.findByRepoIdAndNodeIdAndHash(repoId, "n1", "h1").isEmpty) { "image asset should be gone" }
        assert(trackingEntryRepository.findByRepoIdAndNodeIdAndHash(repoId, "n2", "h2").isPresent) { "video asset should remain" }
    }

    @Test
    fun `delete all assets removes everything for the repo`() {
        val repoId = "assets-repo-all"
        putAsset(repoId, "n1", "h1", "image", "image/jpeg")
        putAsset(repoId, "n2", "h2", "video", "video/mp4")

        mockMvc.perform(delete("/admin/assets/all").param("repoId", repoId).header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deleted").value(2))

        assert(trackingEntryRepository.findAllByRepoIdAndNodeId(repoId, "n1").isEmpty())
        assert(trackingEntryRepository.findAllByRepoIdAndNodeId(repoId, "n2").isEmpty())
    }

    // --- Helpers ------------------------------------------------------------------------------

    private fun storeRegistration(repoId: String, quota: Long, buckets: ExternalBuckets? = null) {
        registrationStorageService.storeRegistration(
            RepositoryRegistration(
                repoId = repoId,
                url = "https://$repoId.example.org/edu-sharing",
                publicKey = "key",
                quota = quota,
                buckets = buckets,
            ),
        )
    }

    private fun trackingEntry(repoId: String, nodeId: String, hash: String, type: String, bucket: String, size: Long) =
        TrackingEntry.of(repoId = repoId, nodeId = nodeId, hash = hash, type = type, bucket = bucket, binarySize = size)

    private fun saveJob(repoId: String, status: RenderingJobStatus): RenderingJob =
        jobRepository.save(
            RenderingJob(
                status = status,
                module = "image",
                esObjectType = "image",
                esObjectId = "node-${java.util.UUID.randomUUID()}",
                repoId = repoId,
                esHash = "hash",
                mimeType = "image/jpeg",
                nodeVersion = "1.0",
            ),
        )

    private fun cacheObject(repoId: String, nodeId: String, hash: String, type: String, mimeType: String) =
        CacheObject(nodeId = nodeId, type = type, hash = hash, repoId = repoId, mimeType = mimeType)

    private fun putAsset(repoId: String, nodeId: String, hash: String, type: String, mimeType: String) {
        val bytes = "test-content-$nodeId".toByteArray()
        val obj = cacheObject(repoId, nodeId, hash, type, mimeType).apply { size = bytes.size.toLong() }
        storageService.putObject(obj, { bytes.inputStream() })
    }
}
