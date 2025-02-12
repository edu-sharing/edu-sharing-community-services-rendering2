package org.edu_sharing.rendering.modules.moodle

import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RequestUserData
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.testUtils.JobDataProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.core.AmqpTemplate

@ExtendWith(MockKExtension::class)
class MoodleJobServiceTest {
    private val mapperMock = mockk<Mapper>()
    private val jobRepository = mockk<RenderingJobRepository>()
    private val subJobRepository = mockk<SubJobRepository>()
    private val amqpTemplate = mockk<AmqpTemplate>()
    private val jobDataProvider = JobDataProvider()

    private lateinit var underTest: MoodleJobService

    @BeforeEach
    fun setup() {
        underTest = MoodleJobService(
            mapper = mapperMock,
            jobRepository = jobRepository,
            subJobRepository = subJobRepository,
            amqpTemplate = amqpTemplate,
        )
        underTest.topicExchangeName = "topicExchange1"
        underTest.jobRoutingKey = "jobRoutingKey1"
        clearAllMocks()
    }

    @Test
    fun testCreateJobThrowsExceptionIfTokenMissing() {
        // Arrange
        val request = mockk<RenderDataRequest>()

        // Act
        assertThrows<IllegalStateException> { underTest.createJob(request, "MOODLE") }
    }

    @Test
    fun testCreateJobThrowsExceptionIfCategoryIdIsMissing() {
        // Arrange
        val request = mockk<RenderDataRequest>()

        // Act
        assertThrows<IllegalStateException> { underTest.createJob(request, "MOODLE") }
    }

    @Test
    fun testCreateJobThrowsExceptionIfNoUserDataProvidedInRequest() {
        // Arrange
        val request = mockk<RenderDataRequest>()

        every { request.userData } returns null
        every { request.nodeId } returns "node123"

        // Act and Assert
        assertThrows<IllegalArgumentException> { underTest.createJob(request, "MOODLE") }
    }

    @Test
    fun testCreateCreatesJobAndReturnsId() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        val userData = RequestUserData(
            authorityName = "authorityName",
            firstName = "firstName",
            surName = "surname",
            userEMail = "mail@mail.de"
        )
        val expectedJob = jobDataProvider.getJobWithoutSubJobs()
        val expectedSubJob = SubJob(
            status = JobStatus.QUEUED,
            routingKey = "jobRoutingKey1",
            parent = expectedJob
        )
        val expectedMessage = MoodleJobMessage(
            id = expectedJob.id.toString(),
            nodeId = expectedJob.esObjectId,
            title = "titletest",
            authorityName = "authorityName",
            userEmail = "mail@mail.de",
            userGivenName = "firstName",
            userSurname = "surname"
        )
        val subJobSlot = slot<SubJob>()

        every { request.userData } returns userData
        every { mapperMock.renderDataRequestToRenderingJob(request, "MOODLE") } returns expectedJob
        every { jobRepository.save(expectedJob) } returns expectedJob
        every { subJobRepository.save(capture(subJobSlot)) } returns expectedSubJob
        every { request.title } returns "titletest"
        justRun { amqpTemplate.convertAndSend("topicExchange1", "jobRoutingKey1", expectedMessage) }

        // Act
        val result = underTest.createJob(request, "MOODLE")

        // Assert
        assert(expectedJob.id.toString() == result)
        assert(subJobSlot.captured.parent == expectedJob)
        assert(subJobSlot.captured.status == JobStatus.QUEUED)
        assert(subJobSlot.captured.routingKey == "jobRoutingKey1")

        verifySequence {
            request.userData
            mapperMock.renderDataRequestToRenderingJob(request, "MOODLE")
            jobRepository.save(expectedJob)
            subJobRepository.save(any())
            request.title
            request.userData
            request.userData
            request.userData
            request.userData
            amqpTemplate.convertAndSend("topicExchange1", "jobRoutingKey1", expectedMessage)
        }
    }

    @Test
    fun testCreateCreatesJobSetsMessageTitleToEmptyStringIfNull() {
        // Arrange
        val request = mockk<RenderDataRequest>()
        val userData = RequestUserData(
            authorityName = "authorityName",
            firstName = "firstName",
            surName = "surname",
            userEMail = "mail@mail.de"
        )
        val expectedJob = jobDataProvider.getJobWithoutSubJobs()
        val expectedSubJob = SubJob(
            status = JobStatus.QUEUED,
            routingKey = "jobRoutingKey1",
            parent = expectedJob
        )
        val expectedMessage = MoodleJobMessage(
            id = expectedJob.id.toString(),
            nodeId = expectedJob.esObjectId,
            title = "",
            authorityName = "authorityName",
            userEmail = "mail@mail.de",
            userGivenName = "firstName",
            userSurname = "surname"
        )
        val subJobSlot = slot<SubJob>()

        every { request.userData } returns userData
        every { mapperMock.renderDataRequestToRenderingJob(request, "MOODLE") } returns expectedJob
        every { jobRepository.save(expectedJob) } returns expectedJob
        every { subJobRepository.save(capture(subJobSlot)) } returns expectedSubJob
        every { request.title } returns null
        justRun { amqpTemplate.convertAndSend("topicExchange1", "jobRoutingKey1", expectedMessage) }

        // Act
        val result = underTest.createJob(request, "MOODLE")

        // Assert
        assert(expectedJob.id.toString() == result)
    }
}