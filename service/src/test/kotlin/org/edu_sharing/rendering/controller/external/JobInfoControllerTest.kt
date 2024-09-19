package org.edu_sharing.rendering.controller.external

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.renderingJob.dto.JobInfoReply
import org.edu_sharing.rendering.renderingJob.dto.JobProgressInfo
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.renderingJob.entity.JobStatus
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.JobInfoService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


@WebMvcTest(JobInfoController::class, excludeAutoConfiguration = [SecurityAutoConfiguration::class])
class JobInfoControllerTest(@Autowired val mockMvc: MockMvc) {
    @MockkBean
    lateinit var jobInfoService: JobInfoService

    @Test
    fun testGetJobInfoReturnsJobInfoFromService() {
        // Arrange
        val jobId = "123"
        val renderingJob = mockk<RenderingJob>()
        every { jobInfoService.getRenderingJob(jobId) } returns renderingJob
        every { jobInfoService.getJobInfo(renderingJob) } returns JobInfoReply(
            mutableListOf(
                JobProgressInfo(
                    status = JobStatus.QUEUED,
                    objectLink = ObjectLink(
                        link = "mylink.de"
                    )
                )
            ),
            RenderModules.IMAGE,
            JobStatus.QUEUED
        )
        // Act
        val result = mockMvc.perform(get("/public/job?jobId=$jobId"))
            .andExpect(status().isOk)
            .andReturn()

        // Assert
        val response = ObjectMapper().readValue(result.response.contentAsString, JobInfoReply::class.java)
        assert(response.status == JobStatus.QUEUED)
        assert(response.module == RenderModules.IMAGE)
        assert(response.jobs.size == 1)
        assert(JobProgressInfo.status == JobStatus.QUEUED)
        assert(JobProgressInfo.objectLink?.link == "mylink.de")
        assert(JobProgressInfo.progress.toInt() == 0)
        assert(JobProgressInfo.quality == 0)
        assert(JobProgressInfo.objectLink?.isHighestQuality == false)
        assert(JobProgressInfo.objectLink?.width == 0)
        assert(JobProgressInfo.objectLink?.height == 0)

        verifySequence {
            jobInfoService.getRenderingJob(jobId)
            jobInfoService.getJobInfo(renderingJob)
        }

        confirmVerified(jobInfoService)
    }
}