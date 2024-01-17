package org.edu_sharing.rendering.service

import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.edu_sharing.rendering.dto.TestResponse
import org.springframework.stereotype.Service
import redis.clients.jedis.JedisPooled

@Service
class TestService(
    private val eduMinioClient: MinioClient,
    private val eduRedisClient: JedisPooled
    ) {
    fun getMessage(): TestResponse {
        val exists = eduMinioClient.bucketExists(BucketExistsArgs.builder().bucket("edu-test-bucket").build())
        eduRedisClient.set("name", "John");
        return TestResponse(if (exists) "exists" else "not exists", eduRedisClient.get("name"))
    }
}