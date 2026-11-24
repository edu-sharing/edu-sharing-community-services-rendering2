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
import org.edu_sharing.rendering.edusharingRepo.entity.ExternalBucket
import org.edu_sharing.rendering.edusharingRepo.entity.ExternalBuckets
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiBucketInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.core.annotation.AnnotationAwareOrderComparator
import java.util.Optional

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
        verify(exactly = 0) { lumiContentManagementService.getContentScope(any()) }
    }

    @Test
    fun `a bucket the service does not own is routed to lumi`() {
        every { bucketStrategy.isManagedBucket("lumi-contentbucket", "repo1") } returns false
        every { lumiContentManagementService.getContentScope("repo1") } returns "lumi-contentbucket"

        assertSame(lumi, registry.getBucketManagerByBucketName("lumi-contentbucket", "repo1"))
        verify(exactly = 1) { lumiContentManagementService.getContentScope("repo1") }
    }

    @Test
    fun `lumi and rendering sharing one bucket via prefixes are routed apart by scope name`() {
        // Rendering under shared/rendering/, lumi under shared/lumi/content/: the tracking bucket of an
        // H5P entry is the scope name "shared/lumi/content", never the bare bucket.
        every { bucketStrategy.isManagedBucket("shared", "repo1") } returns true
        every { bucketStrategy.isManagedBucket("shared/lumi/content", "repo1") } returns false
        every { lumiContentManagementService.getContentScope("repo1") } returns "shared/lumi/content"

        assertSame(rs2, registry.getBucketManagerByBucketName("shared", "repo1"))
        assertSame(lumi, registry.getBucketManagerByBucketName("shared/lumi/content", "repo1"))
    }

    @Test
    fun `the registry keeps both quotas of a shared bucket`() {
        val registrations = mockk<RepositoryRegistrationStorageService>()
        every { registrations.getRegistrationByRepoId("repo1") } returns Optional.of(
            RepositoryRegistration(
                repoId = "repo1", url = "https://r", publicKey = "k",
                optionalModules = mutableListOf(H5pRenderModule.MODULE_NAME),
                buckets = ExternalBuckets(renderingBucket = ExternalBucket(name = "shared", quota = 100, keyPrefix = "rendering"))
            )
        )
        every { lumiContentManagementService.getContentBucketInfo("repo1") } returns
            LumiBucketInfo(contentBucket = "shared", contentBucketQuota = 200, contentKeyPrefix = "lumi/content")
        val sharedRs2 = Rs2StorageManager(mockk<StorageService>(), mockk<Mapper>(), bucketStrategy, registrations)
        val sharedLumi = LumiStorageManager(lumiContentManagementService, registrations)
        val sharedRegistry = StorageManagerRegistry(listOf(sharedRs2, sharedLumi))

        assertEquals(
            mapOf("shared" to 100L, "shared/lumi/content" to 200L),
            sharedRegistry.getManagedBucketQuotas("repo1")
        )
    }
}
