package org.edu_sharing.rendering.modules.av.video

import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith


@ExtendWith(MockKExtension::class)
class VideoConverterConfigTest {

    private lateinit var config: VideoConverterConfig

    @BeforeEach
    fun setUp() {
        config = VideoConverterConfig().apply {
            resolutions = mapOf(
                "240" to VideoResolutionItemConfig(priority = 1),
                "480" to VideoResolutionItemConfig(priority = 2),
                "720" to VideoResolutionItemConfig(priority = 3)
            )
        }
    }

    @Test
    fun `getResolutions should return list of resolution integers`() {
        val result = config.getResolutions()
        assertTrue(result.containsAll(listOf(240, 480, 720)))
    }

    @Test
    fun `getMaxPriority should return the highest priority`() {
        assertEquals(3, config.getMaxPriority())
    }

    @Test
    fun `getPriority should return priority for existing resolution`() {
        assertEquals(2, config.getPriority(480, default = -1))
    }

    @Test
    fun `getPriority should return default when resolution does not exist`() {
        assertEquals(-1, config.getPriority(1080, default = -1))
    }

    @Test
    fun `isEmpty should return false when resolutions are present`() {
        assertFalse(config.isEmpty())
    }

    @Test
    fun `getMaxResolution should return highest resolution`() {
        assertEquals(720, config.getMaxResolution())
    }

    @Test
    fun `getMinResolution should return lowest resolution`() {
        assertEquals(240, config.getMinResolution())
    }

    @Test
    fun `getPossibleResolutions should return original height if less than min`() {
        val result = config.getPossibleResolutions(100)
        assertEquals(listOf(100), result)
    }

    @Test
    fun `getPossibleResolutions should return resolutions less than or equal to original height`() {
        val result = config.getPossibleResolutions(500)
        assertEquals(listOf(240, 480), result.sorted())
    }

    @Test
    fun `getPossibleResolutions should return all resolutions if original height is null`() {
        val result = config.getPossibleResolutions(null)
        assertEquals(listOf(240, 480, 720), result.sorted())
    }
}
