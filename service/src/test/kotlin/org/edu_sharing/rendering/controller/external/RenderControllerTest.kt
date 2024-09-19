package org.edu_sharing.rendering.controller.external

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.edu_sharing.rendering.dto.*
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.service.RenderDataService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(RenderController::class, excludeAutoConfiguration = [SecurityAutoConfiguration::class])
class RenderControllerTest(@Autowired val mockMvc: MockMvc) {
    @MockkBean
    lateinit var service: RenderDataService

    @Test
    fun testGetRenderDataReturnsResponseFromService() {
        // Arrange
        val jsonBody = "{\n" +
                "\t\"nodeId\": \"TEST_superfile.h5p\",\n" +
                "\t\"size\": -1,\n" +
                "\t\"type\": \"h5p\",\n" +
                "\t\"hash\": \"superhash\",\n" +
                "\t\"mimeType\": \"application/zip\",\n" +
                "\t\"version\": \"1.0\",\n" +
                "\t\"repoId\": \"enterprise-docker-maven-fixes-8-1\"\n" +
                "}"
        val bodySlot = slot<RenderDataRequest>()
        val response = RenderDataResponse(
            objectLinks = listOf(ObjectLink(link = "mylink")),
            module = RenderModules.IMAGE
        )

        every {
            service.getRenderData(capture(bodySlot))
        } returns response

        // Act
        val result = mockMvc.perform(
            MockMvcRequestBuilders.post("/public/renderdata")
                .content(jsonBody)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andReturn()

        // Assert
        val returnedJson = result.response.contentAsString
        val responseObject = ObjectMapper().readValue(returnedJson, RenderDataResponse::class.java)

        assert(responseObject.module == RenderModules.IMAGE)
        assert(responseObject.objectLinks?.size == 1)
        assert(responseObject.objectLinks?.first()?.link == "mylink")
        assert(responseObject.jobId == null)

        verify (exactly = 1) {
            service.getRenderData(capture(bodySlot))
        }
        confirmVerified(service)
    }
}