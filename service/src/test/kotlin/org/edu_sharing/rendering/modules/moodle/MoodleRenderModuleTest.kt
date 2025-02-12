package org.edu_sharing.rendering.modules.moodle

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class MoodleRenderModuleTest {
    private val expirationTime = 55L
    private val moodleJobService = mockk<MoodleJobService>()
    private val repositoryRegistrationStorageService = mockk<RepositoryRegistrationStorageService>()

    lateinit var underTest: MoodleRenderModule

    @BeforeEach
    fun setup() {
        underTest = MoodleRenderModule(expirationTime, moodleJobService, repositoryRegistrationStorageService)
        clearAllMocks()
    }

    @Test
    fun testModuleReturnsMoodleModule() {
        assert(underTest.module() == "MOODLE")
    }

    @Test
    fun testHandleReturnsResponseWithNewJobId() {
        // Arrange
        val request = mockk<RenderDataRequest>()

        every { moodleJobService.createJob(request, "MOODLE") } returns "job123"

        // Act
        val result = underTest.handle(request)

        // Assert
        assert(result.jobId == "job123")
        assert(result.objectLinks!!.isEmpty())
        assert(result.module == "MOODLE")

        verify(exactly = 1) { moodleJobService.createJob(request, "MOODLE") }
        confirmVerified(moodleJobService)
    }

    @Test
    fun testGetObjectLinkFromJobDataReturnsSubJobMessage() {
        // Arrange
        val subJob = mockk<SubJob>()
        val renderingJob = mockk<RenderingJob>()

        every { subJob.message } returns "messagewithlink"

        // Act
        val result = underTest.getObjectLinkFromJobData(subJob, renderingJob)

        // Assert
        assert(result?.link == "messagewithlink")

        verify (exactly = 1) { subJob.message }
        confirmVerified(subJob)
    }

    @Test
    fun testGetObjectLinkFromJobDataReturnsEmptyStringIfSubJobMessageIsNull() {
        // Arrange
        val subJob = mockk<SubJob>()
        val renderingJob = mockk<RenderingJob>()

        every { subJob.message } returns null

        // Act
        val result = underTest.getObjectLinkFromJobData(subJob, renderingJob)

        // Assert
        assert(result?.link == "")

        verify (exactly = 1) { subJob.message }
        confirmVerified(subJob)
    }

    @Test
    fun getRemoteServiceMethodReturnsRemoteServiceMethodForMoodleCourses() {
        assert(underTest.getRemoteServiceMethod() == "restore")
    }

    @Test
    fun getNodePermissionExpirationTimeReturnsSetExpirationTime() {
        assert(underTest.getNodePermissionExpirationTime() == 55L)
    }
}