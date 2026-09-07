package org.edu_sharing.rendering.storage

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.entity.ExternalBucket
import org.edu_sharing.rendering.edusharingRepo.entity.ExternalBuckets
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.storage.bucket.BucketStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional

@ExtendWith(MockKExtension::class)
class Rs2StorageManagerTest {
    private val storageService = mockk<StorageService>()
    private val mapper = mockk<Mapper>()
    private val bucketStrategy = mockk<BucketStrategy>()
    private val repositoryRegistrationStorageService = mockk<RepositoryRegistrationStorageService>()

    private val underTest = Rs2StorageManager(storageService, mapper, bucketStrategy, repositoryRegistrationStorageService)

    private fun registration(buckets: ExternalBuckets?) = Optional.of(
        RepositoryRegistration(repoId = "repo1", url = "https://repo1.example.org", publicKey = "key", buckets = buckets)
    )

    @Test
    fun `reports the rendering bucket quota when configured`() {
        every { repositoryRegistrationStorageService.getRegistrationByRepoId("repo1") } returns
            registration(ExternalBuckets(renderingBucket = ExternalBucket(name = "rendering2", quota = 1000)))

        assertEquals(mapOf("rendering2" to 1000L), underTest.getManagedBucketQuotas("repo1"))
    }

    @Test
    fun `reports no quota when the rendering bucket has none configured`() {
        every { repositoryRegistrationStorageService.getRegistrationByRepoId("repo1") } returns
            registration(ExternalBuckets(renderingBucket = ExternalBucket(name = "rendering2", quota = 0)))

        assertEquals(emptyMap<String, Long>(), underTest.getManagedBucketQuotas("repo1"))
    }

    @Test
    fun `reports no quota when no buckets are configured at all`() {
        every { repositoryRegistrationStorageService.getRegistrationByRepoId("repo1") } returns registration(null)

        assertEquals(emptyMap<String, Long>(), underTest.getManagedBucketQuotas("repo1"))
    }

    @Test
    fun `never reports a quota for the temp bucket`() {
        every { repositoryRegistrationStorageService.getRegistrationByRepoId("repo1") } returns
            registration(
                ExternalBuckets(
                    renderingBucket = ExternalBucket(name = "rendering2", quota = 1000),
                    tempBucket = ExternalBucket(name = "temp", quota = 500),
                )
            )

        assertEquals(mapOf("rendering2" to 1000L), underTest.getManagedBucketQuotas("repo1"))
    }

    @Test
    fun `isBucketOwner checks the given bucket name, not just any managed bucket`() {
        // Regression test: isBucketOwner used to ignore its bucketName parameter entirely.
        every { bucketStrategy.isManagedBucket("rendering2", "repo1") } returns true
        every { bucketStrategy.isManagedBucket("some-other-bucket", "repo1") } returns false

        assertTrue(underTest.isBucketOwner("rendering2", "repo1"))
        assertFalse(underTest.isBucketOwner("some-other-bucket", "repo1"))
    }
}
