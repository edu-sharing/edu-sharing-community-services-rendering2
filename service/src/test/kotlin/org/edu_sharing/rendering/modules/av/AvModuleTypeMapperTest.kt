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
        assert(result.size == 1)
        assert(result[0].first.type == null)
        assert(result[0].first.mimeTypeSuffix == null)
        assert(result[0].first.mimeTypePrefix == "audio")
        assert(result[0].second == underTest)
    }

    @Test
    fun testModuleTypeAssociationsReturnsDefinitionWithMimetypePrefixForVideo() {
        // Act
        val result = underTest.moduleTypeAssociations()

        // Assert
        assert(result.size == 1)
        assert(result[0].first.type == null)
        assert(result[0].first.mimeTypeSuffix == null)
        assert(result[0].first.mimeTypePrefix == "video")
        assert(result[0].second == underTest)
    }
}