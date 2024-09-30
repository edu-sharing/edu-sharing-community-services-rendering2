package org.edu_sharing.rendering.modules.scorm

import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.modules.moodle.MoodleJobService
import org.edu_sharing.rendering.modules.moodle.ScormRenderModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ScormRenderModuleTest {
    private val moodleJobService = mockk<MoodleJobService>()
    private val nodeExpiration = 99L

    lateinit var underTest: ScormRenderModule

    @BeforeEach
    fun setup() {
        underTest = ScormRenderModule(nodeExpiration, moodleJobService)
    }

    @Test
    fun testModuleReturnsScormModule() {
        assert(underTest.module() == "SCORM")
    }

    @Test
    fun testGetNodeExpirationTimeReturnsSetTime() {
        assert(underTest.getNodePermissionExpirationTime() == nodeExpiration)
    }

    @Test
    fun testGetRemoteServiceMethodReturnsScormMethod() {
        assert(underTest.getRemoteServiceMethod() == "scorm")
    }
}