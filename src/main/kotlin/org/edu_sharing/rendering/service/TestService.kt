package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.dto.TestResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service

@Service
class TestService(
    @Qualifier("webApplicationContext") private val resourceLoader: ResourceLoader,
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)
    fun getMessage(): TestResponse {
        return TestResponse("vid", "enc")
    }
}