package org.edu_sharing.rendering.modules.binder.dto

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test

class ProgressInfoDeserializerTest {
    @Test
    fun testEventIsProperlyDeserializedWithFailedMessage() {
        // Arrange
        val json = """{"phase": "failed", "message": "Reason for failure"}"""
        val objectMapper = jacksonObjectMapper()

        // Act
        val result = objectMapper.readValue(json, BinderSseEvent::class.java)

        // Assert
        assert(result.phase == "failed")
        assert(result.message == "Reason for failure")
        assert(result.imageName == null)
        assert(result.progress == null)
        assert(result.url == null)
        assert(result.token == null)
    }

    @Test
    fun testEventIsProperlyDeserializedWithBuiltMessage() {
        // Arrange
        val json = """{"phase": "built", "message": "coolMessage", "imageName": "superImage"}"""
        val objectMapper = jacksonObjectMapper()

        // Act
        val result = objectMapper.readValue(json, BinderSseEvent::class.java)

        // Assert
        assert(result.phase == "built")
        assert(result.message == "coolMessage")
        assert(result.imageName == "superImage")
        assert(result.progress == null)
        assert(result.url == null)
        assert(result.token == null)
    }

    @Test
    fun testEventIsProperlyDeserializedWithPushingMessage() {
        // Arrange
        val json = """{"phase": "pushing", "message": "superCoolMessage", "progress": {"layer1":  {"current": 4, "total": 10}, "layer2": {"current": 8, "total": 12}, "layer3": "Pushed", "layer4": "Layer already exists"}}"""
        val objectMapper = jacksonObjectMapper()

        // Act
        val result = objectMapper.readValue(json, BinderSseEvent::class.java)

        // Assert
        assert(result.phase == "pushing")
        assert(result.message == "superCoolMessage")
        assert(result.imageName == null)
        assert(result.token == null)
        assert(result.progress != null)

        assert(result.progress!!.layer1.progressObject != null)
        assert(result.progress.layer1.progressObject!!.current == 4L)
        assert(result.progress.layer1.progressObject.total == 10L)
        assert(result.progress.layer1.progressString == null)

        assert(result.progress.layer2.progressObject != null)
        assert(result.progress.layer2.progressObject!!.current == 8L)
        assert(result.progress.layer2.progressObject.total == 12L)
        assert(result.progress.layer2.progressString == null)

        assert(result.progress.layer3.progressObject == null)
        assert(result.progress.layer3.progressString == "Pushed")

        assert(result.progress.layer4.progressObject == null)
        assert(result.progress.layer4.progressString == "Layer already exists")
    }

    @Test
    fun testEventIsProperlyDeserializedWithReadyMessage() {
        // Arrange
        val json = """{"phase": "ready", "message": "Human readable message", "url": "full-url-of-notebook-server", "token": "notebook-server-token"}"""
        val objectMapper = jacksonObjectMapper()

        // Act
        val result = objectMapper.readValue(json, BinderSseEvent::class.java)

        // Assert
        assert(result.phase == "ready")
        assert(result.message == "Human readable message")
        assert(result.imageName == null)
        assert(result.token == "notebook-server-token")
        assert(result.progress == null)
        assert(result.url == "full-url-of-notebook-server")
    }
}