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
    fun `an edu-sharing revision suffix is cut off, a pre-release is not`() {
        assertEquals("8.2.10", TestImages.upstreamVersion("8.2.10-1"))
        assertEquals("4.3.7", TestImages.upstreamVersion("4.3.7"))
        assertEquals("1.0.0-rc.5", TestImages.upstreamVersion("1.0.0-rc.5"))
        assertEquals("1.0.0-rc.5", TestImages.upstreamVersion("1.0.0-rc.5-1"))
    }

    @Test
    fun `the images are the upstream ones the Testcontainers modules expect`() {
        assertEquals("mongo", TestImages.mongodb().unversionedPart)
        assertEquals("redis", TestImages.redis().unversionedPart)
        assertEquals("rabbitmq", TestImages.rabbitmq().unversionedPart)
        assertTrue(TestImages.rabbitmq().versionPart.endsWith("-management"))
    }
}
