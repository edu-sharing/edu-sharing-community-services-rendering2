package org.edu_sharing.rendering.modules.h5p.lumi

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.modules.h5p.lumi.dto.LumiBucketInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class LumiStorageManagerTest {
    private val lumiContentManagementService = mockk<LumiContentManagementService>()

    private val underTest = LumiStorageManager(lumiContentManagementService)

    @Test
    fun `reports the content bucket quota when lumi has one configured`() {
        every { lumiContentManagementService.getContentBucketInfo("repo1") } returns
            LumiBucketInfo(contentBucket = "lumi-contentbucket", contentBucketQuota = 5_000_000_000L)

        assertEquals(mapOf("lumi-contentbucket" to 5_000_000_000L), underTest.getManagedBucketQuotas("repo1"))
    }

    @Test
    fun `reports no quota when lumi has none configured`() {
        every { lumiContentManagementService.getContentBucketInfo("repo1") } returns
            LumiBucketInfo(contentBucket = "lumi-contentbucket", contentBucketQuota = null)

        assertEquals(emptyMap<String, Long>(), underTest.getManagedBucketQuotas("repo1"))
    }

    @Test
    fun `reports no quota instead of failing when lumi is unreachable`() {
        // Called on every admin dashboard poll and by the daily CacheCleaner — an unreachable lumi
        // must not break either, see LumiStorageManager.getManagedBucketQuotas.
        every { lumiContentManagementService.getContentBucketInfo("repo1") } throws RuntimeException("connection refused")

        assertEquals(emptyMap<String, Long>(), underTest.getManagedBucketQuotas("repo1"))
    }
}
