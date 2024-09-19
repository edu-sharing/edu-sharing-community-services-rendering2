package org.edu_sharing.rendering.modules

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.exception.ModuleNotRegisteredException
import org.edu_sharing.rendering.modules.av.audio.AudioRenderModule
import org.edu_sharing.rendering.modules.video.VideoRenderModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ModuleRegistryTest () {

    @Test
    fun testGetRenderModuleReturnsProperModule() {
        // Arrange
        val audioMock = mockk<AudioRenderModule>()
        val videoMock = mockk<VideoRenderModule>()

        every { audioMock.module() } returns RenderModules.AUDIO
        every { videoMock.module() } returns RenderModules.VIDEO

        val underTest = ModuleRegistry(listOf(audioMock, videoMock))

        // Act
        val resultVideo: RenderModule = underTest.getRenderModule(RenderModules.VIDEO)
        val resultAudio: RenderModule = underTest.getRenderModule(RenderModules.AUDIO)

        // Assert
        assert(resultVideo == videoMock)
        assert(resultAudio == audioMock)
    }

    @Test
    fun testGetRenderModuleThrowsExceptionOnNotRegisteredModule() {
        // Arrange
        val audioMock = mockk<AudioRenderModule>()
        val videoMock = mockk<VideoRenderModule>()

        every { audioMock.module() } returns RenderModules.AUDIO
        every { videoMock.module() } returns RenderModules.VIDEO

        val underTest = ModuleRegistry(listOf(audioMock, videoMock))

        // Act
        assertThrows<ModuleNotRegisteredException> { underTest.getRenderModule<RenderModule>(RenderModules.UNKNOWN) }
    }
}