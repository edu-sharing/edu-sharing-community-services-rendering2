package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.TestResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TestService(
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)
    fun getMessage(): TestResponse {
        return TestResponse("session", "set")
    }
}