package org.edu_sharing.rendering.storage.bucket

import org.edu_sharing.rendering.edusharingRepo.entity.ExternalBucket
import org.edu_sharing.rendering.storage.bucket.ExternalBucketScopeValidator.Companion.findOverlaps
import org.edu_sharing.rendering.storage.bucket.ExternalBucketScopeValidator.Scope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExternalBucketScopeValidatorTest {

    private fun scope(label: String, bucket: String, prefix: String) =
        Scope(label, bucket, ExternalBucket.normalizeKeyPrefix(prefix))

    private fun overlaps(bucketA: String, prefixA: String, bucketB: String, prefixB: String) =
        findOverlaps(listOf(scope("a", bucketA, prefixA), scope("b", bucketB, prefixB)))

    @Test
    fun `same bucket without prefixes overlaps`() {
        assertEquals(1, overlaps("shared", "", "shared", "").size)
    }

    @Test
    fun `same bucket and same prefix overlaps regardless of slashes`() {
        assertEquals(1, overlaps("shared", "/temp/", "shared", "temp").size)
    }

    @Test
    fun `bucket root overlaps with any prefix`() {
        assertEquals(1, overlaps("shared", "", "shared", "temp/").size)
    }

    @Test
    fun `nested prefixes overlap`() {
        assertEquals(1, overlaps("shared", "a/", "shared", "a/b/").size)
    }

    @Test
    fun `prefixes that only share leading characters do not overlap`() {
        assertTrue(overlaps("shared", "a/", "shared", "ab/").isEmpty())
    }

    @Test
    fun `distinct prefixes do not overlap`() {
        assertTrue(overlaps("shared", "rendering/", "shared", "temp/").isEmpty())
    }

    @Test
    fun `different buckets with equal prefixes do not overlap`() {
        assertTrue(overlaps("one", "temp/", "two", "temp/").isEmpty())
    }
}
