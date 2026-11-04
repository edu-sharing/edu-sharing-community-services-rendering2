package org.edu_sharing.rendering.storage

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNull
import kotlin.test.assertSame

class StorageManagerRegistryTest {

    private fun manager(owns: () -> Boolean) = mockk<StorageManager> {
        every { isBucketOwner(any(), any()) } answers { owns() }
    }

    @Test
    fun `a failing owner check does not hide the manager that owns the bucket`() {
        val failing = manager { throw IllegalStateException("lumi unreachable") }
        val owner = manager { true }

        val registry = StorageManagerRegistry(listOf(failing, owner))

        assertSame(owner, registry.getBucketManagerByBucketName("rs2-image", "repo"))
    }

    @Test
    fun `the failure is propagated when no manager claims the bucket`() {
        val failing = manager { throw IllegalStateException("lumi unreachable") }
        val other = manager { false }

        val registry = StorageManagerRegistry(listOf(failing, other))

        assertThrows<IllegalStateException> { registry.getBucketManagerByBucketName("file-h5p", "repo") }
    }

    @Test
    fun `no manager and no failure yields null`() {
        val registry = StorageManagerRegistry(listOf(manager { false }))

        assertNull(registry.getBucketManagerByBucketName("unknown", "repo"))
    }
}
