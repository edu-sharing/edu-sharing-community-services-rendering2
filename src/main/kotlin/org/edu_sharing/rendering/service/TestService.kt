package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.dto.TestResponse
import org.edu_sharing.rendering.processing.h5p.H5pUploadService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TestService(private val h5pUploadService: H5pUploadService) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getMessage(): TestResponse {
        val testObject = CacheObject(
            nodeId = "TEST_file.h5p",
            type = "h5p",
            hash = "myh5pHash",
        )
        try {
            val contentId = h5pUploadService.getContentId(cacheObject = testObject)
            return TestResponse(contentId, "found")
        } catch (exception: Exception) {
            logger.error(exception.message)
        }
        return TestResponse("session", "set")
    }
}