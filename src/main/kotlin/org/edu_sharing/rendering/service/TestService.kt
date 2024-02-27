package org.edu_sharing.rendering.service

import io.minio.MinioClient
import org.edu_sharing.rendering.dto.TestResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TestService(
    private val eduMinioClient: MinioClient,
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)
    fun getMessage(): TestResponse {
        logger.info("INFO")
        logger.warn("WARN")
        logger.error("ERROR")
        return TestResponse("just", "logging")
    }
}