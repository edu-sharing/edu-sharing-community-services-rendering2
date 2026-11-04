package org.edu_sharing.rendering.storage

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.h5p.lumi.LumiContentManagementService
import org.edu_sharing.rendering.modules.h5p.lumi.LumiStorageManager
import org.edu_sharing.rendering.storage.bucket.BucketStrategy
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.core.annotation.AnnotationAwareOrderComparator

@ExtendWith(MockKExtension::class)
class StorageManagerOrderTest {
    private val bucketStrategy = mockk<BucketStrategy>()
    private val lumiContentManagementService = mockk<LumiContentManagementService>()

    private val rs2 = Rs2StorageManager(
        mockk<StorageService>(),
        mockk<Mapper>(),
        bucketStrategy,
        mockk<RepositoryRegistrationStorageService>()
    )
    private val lumi = LumiStorageManager(lumiContentManagementService, mockk<RepositoryRegistrationStorageService>())

    // Spring sorts the injected List<StorageManager> like this; deliberately start in the wrong order.
    private val registry = StorageManagerRegistry(
        listOf<StorageManager>(lumi, rs2).sortedWith(AnnotationAwareOrderComparator.INSTANCE)
    )

    @Test
    fun `Rs2StorageManager is ordered before LumiStorageManager`() {
        assertTrue(registry.getStorageManagers().first() is Rs2StorageManager)
        assertTrue(registry.getStorageManagers().last() is LumiStorageManager)
    }

    @Test
    fun `an own bucket is resolved without asking lumi`() {
        every { bucketStrategy.isManagedBucket("rendering-bucket", "repo1") } returns true

        assertSame(rs2, registry.getBucketManagerByBucketName("rendering-bucket", "repo1"))
        verify(exactly = 0) { lumiContentManagementService.getContentBucket(any()) }
    }

    @Test
    fun `a bucket the service does not own is routed to lumi`() {
        every { bucketStrategy.isManagedBucket("lumi-contentbucket", "repo1") } returns false
        every { lumiContentManagementService.getContentBucket("repo1") } returns "lumi-contentbucket"

        assertSame(lumi, registry.getBucketManagerByBucketName("lumi-contentbucket", "repo1"))
        verify(exactly = 1) { lumiContentManagementService.getContentBucket("repo1") }
    }
}
