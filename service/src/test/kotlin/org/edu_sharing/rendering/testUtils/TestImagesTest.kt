package org.edu_sharing.rendering.testUtils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TestImagesTest {

    @AfterEach
    fun clearOverrides() {
        System.clearProperty("test.image.redis")
    }

    @Test
    fun `the images are filled in from the root pom, not left as placeholders`() {
        listOf(TestImages.mongodb(), TestImages.redis(), TestImages.rabbitmq()).forEach {
            assertFalse(it.asCanonicalNameString().contains("@"), "unfiltered placeholder in $it")
            assertTrue(it.versionPart.isNotEmpty() && it.versionPart != "latest", "no fixed version in $it")
        }
    }

    @Test
    fun `the tests run the edu-sharing images the deployment runs`() {
        assertTrue(TestImages.mongodb().repository.endsWith("/edu_sharing-community-common-docker-mongodb"), TestImages.mongodb().toString())
        assertTrue(TestImages.redis().repository.endsWith("/edu_sharing-community-common-docker-redis"), TestImages.redis().toString())
        assertTrue(TestImages.rabbitmq().repository.endsWith("/edu_sharing-community-common-docker-rabbitmq"), TestImages.rabbitmq().toString())
    }

    @Test
    fun `a system property replaces the whole image for one run`() {
        System.setProperty("test.image.redis", "redis:7.0.10")
        assertEquals("redis:7.0.10", TestImages.redis().asCanonicalNameString())
    }
}
