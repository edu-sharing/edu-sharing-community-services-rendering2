package org.edu_sharing.rendering.storage

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketResponse
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.S3Exception
import java.time.Duration
import java.util.function.Consumer

class S3HealthIndicatorTest {

    private val s3Client = mockk<S3Client>()
    private val underTest = S3HealthIndicator(s3Client, S3HealthProperties("probe", Duration.ofSeconds(2)))

    @Test
    fun `up when the probe bucket exists`() {
        every { s3Client.headBucket(any<Consumer<HeadBucketRequest.Builder>>()) } returns
            HeadBucketResponse.builder().build()
        assertEquals(Status.UP, underTest.health().status)
    }

    @Test
    fun `up when the probe bucket is missing - endpoint answered`() {
        every { s3Client.headBucket(any<Consumer<HeadBucketRequest.Builder>>()) } throws
            NoSuchBucketException.builder().statusCode(404).build()
        assertEquals(Status.UP, underTest.health().status)
    }

    @Test
    fun `down on other S3 errors and on timeouts`() {
        every { s3Client.headBucket(any<Consumer<HeadBucketRequest.Builder>>()) } throws
            S3Exception.builder().statusCode(403).message("denied").build()
        assertEquals(Status.DOWN, underTest.health().status)

        every { s3Client.headBucket(any<Consumer<HeadBucketRequest.Builder>>()) } throws
            RuntimeException("Apache timeout")
        assertEquals(Status.DOWN, underTest.health().status)
    }
}
