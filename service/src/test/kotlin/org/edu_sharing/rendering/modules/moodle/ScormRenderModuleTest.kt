package org.edu_sharing.rendering.modules.moodle

import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ScormRenderModuleTest {
    private val moodleJobService = mockk<MoodleJobService>()
    private val repositoryRegistrationStorageService = mockk<RepositoryRegistrationStorageService>()
    private val nodeExpiration = 99L

    lateinit var underTest: ScormRenderModule

    /*@BeforeEach
    fun setup() {
        underTest = ScormRenderModule(nodeExpiration, moodleJobService, repositoryRegistrationStorageService)
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
    }*/
}