package org.edu_sharing.rendering.modules.av

import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.modules.av.audio.AudioRenderModule
import org.edu_sharing.rendering.modules.av.video.VideoRenderModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AvModuleTypeMapperTest {

    private val audioModule = mockk<AudioRenderModule>()
    private val videoModule = mockk<VideoRenderModule>()

    private val underTest = AvModuleTypeMapper(
        audioRenderModule = audioModule,
        videoRenderModule = videoModule
    )

    @Test
    fun testModuleTypeAssociationsReturnsDefinitionWithMimetypePrefixForAudio() {
        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        val audioEntry = result.first { it.second == audioModule }
        assert(audioEntry.first.type == null)
        assert(audioEntry.first.mimeTypeSuffix == null)
        assert(audioEntry.first.mimeTypePrefix == "audio")
    }

    @Test
    fun testModuleTypeAssociationsReturnsDefinitionWithMimetypePrefixForVideo() {
        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        val videoEntry = result.first { it.second == videoModule }
        assert(videoEntry.first.type == null)
        assert(videoEntry.first.mimeTypeSuffix == null)
        assert(videoEntry.first.mimeTypePrefix == "video")
    }
}