package org.edu_sharing.rendering.core

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc

@WebMvcTest(RenderController::class, excludeAutoConfiguration = [SecurityAutoConfiguration::class])
class RenderControllerTest(@Autowired val mockMvc: MockMvc) {

    /*@MockkBean
    lateinit var service: RenderDataService

    @Test
    fun testGetRenderDataReturnsResponseFromService() {
        val moduleName = "MYMODULE"

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
            module = moduleName
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

        assert(responseObject.module == moduleName)
        assert(responseObject.objectLinks?.size == 1)
        assert(responseObject.objectLinks?.first()?.link == "mylink")
        assert(responseObject.jobId == null)

        verify (exactly = 1) {
            service.getRenderData(capture(bodySlot))
        }
        confirmVerified(service)
    }*/
}