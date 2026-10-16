package org.edu_sharing.rendering.modules.av.video

import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource


@ExtendWith(MockKExtension::class)
class VideoConverterConfigTest {

    private lateinit var config: VideoConverterConfig

    @BeforeEach
    fun setUp() {
        config = VideoConverterConfig().apply {
            resolutions = listOf(240, 480, 720)
            priorities = mapOf(240 to 1, 480 to 2, 720 to 3)
        }
    }

    // application.properties first, then what Helm/compose deliver on top (a later source wins, as an env var would).
    private fun bind(vararg overrides: Pair<String, String>): VideoConverterConfig {
        val applicationProperties = MapConfigurationPropertySource(mapOf(
            "app.converter.video.resolutions" to "480,720,1080",
            "app.converter.video.priorities.480" to "2",
            "app.converter.video.priorities.720" to "1",
            "app.converter.video.priorities.1080" to "0"
        ))
        val deployment = MapConfigurationPropertySource(overrides.toMap())
        return Binder(deployment, applicationProperties)
            .bindOrCreate("app.converter.video", Bindable.of(VideoConverterConfig::class.java))
    }

    @Test
    fun `binds the comma-separated resolutions Helm and compose deliver`() {
        val bound = bind("app.converter.video.resolutions" to "480,720")
        assertEquals(listOf(480, 720), bound.resolutions)
        assertEquals(listOf(480, 720), bound.getPossibleResolutions(null))
    }

    @Test
    fun `dropping a height keeps the queue's max priority`() {
        // x-max-priority of the durable AV queue comes from here and cannot be redeclared with another value
        val bound = bind("app.converter.video.resolutions" to "720,1080")
        assertEquals(2, bound.getMaxPriority())
        assertEquals(1, bound.getPriority(720, 0))
    }

    @Test
    fun `validate rejects an empty resolution list`() {
        assertThrows<IllegalArgumentException> { VideoConverterConfig().validate() }
        assertThrows<IllegalArgumentException> { VideoConverterConfig().apply { resolutions = listOf(0) }.validate() }
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
        assertEquals(listOf(240, 480), result)
    }

    @Test
    fun `getPossibleResolutions should return all resolutions if original height is null`() {
        val result = config.getPossibleResolutions(null)
        assertEquals(listOf(240, 480, 720), result)
    }
}
