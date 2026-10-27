package org.edu_sharing.rendering.testUtils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TestImagesTest {

    @Test
    fun `the image versions are filled in from the root pom, not left as placeholders`() {
        listOf(TestImages.mongodb(), TestImages.redis(), TestImages.rabbitmq()).forEach {
            assertFalse(it.versionPart.contains("@"), "unfiltered placeholder in $it")
            assertTrue(Regex("""\d+\.\d+(\.\d+)?(-management)?""").matches(it.versionPart), "unexpected tag in $it")
        }
    }

    @Test
    fun `the images are the upstream ones the Testcontainers modules expect`() {
        assertEquals("mongo", TestImages.mongodb().unversionedPart)
        assertEquals("redis", TestImages.redis().unversionedPart)
        assertEquals("rabbitmq", TestImages.rabbitmq().unversionedPart)
        assertTrue(TestImages.rabbitmq().versionPart.endsWith("-management"))
    }
}
