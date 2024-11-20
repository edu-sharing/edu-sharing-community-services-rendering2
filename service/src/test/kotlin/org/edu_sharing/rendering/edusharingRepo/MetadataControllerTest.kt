package org.edu_sharing.rendering.edusharingRepo

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.edu_sharing.rendering.edusharingRepo.entity.RendererKeyConfig
import org.edu_sharing.rendering.edusharingRepo.services.MetadataService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*


@WebMvcTest(
    MetadataController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class],
    properties = [
        "app.appCaption=testcaption",
        "app.appId=testappid",
        "app.public.port=1234",
        "app.public.url=http://renderer2.io"
    ]
)
class MetadataControllerTest(@Autowired val mockMvc: MockMvc) {

    @MockkBean
    lateinit var service: MetadataService

    @Test
    fun testGetMetadataReturnsProperXmlMetadata() {
        // Arrange
        val config = mockk<RendererKeyConfig>()

        every { config.publicKey } returns "somekey"
        every { service.getConfig() } returns config

        // Act
        val result = mockMvc.perform(get("/public/metadata"))
            .andExpect(status().isOk)
            .andReturn()

        // Assert
        val props = Properties()
        props.loadFromXML(result.response.contentAsString.byteInputStream())
        assert(props.size == 7)
        assert(props.containsKey("public_key"))
        assert(props["public_key"] == "somekey")
        assert(props.containsKey("appcaption"))
        assert(props["appcaption"] == "testcaption")
        assert(props.containsKey("port"))
        assert(props["port"] == "1234")
        assert(props.containsKey("appid"))
        assert(props["appid"] == "testappid")
        assert(props.containsKey("host"))
        assert(props["host"] == "http://renderer2.io")
        assert(props.containsKey("trustedclient"))
        assert(props["trustedclient"] == "true")
        assert(props.containsKey("type"))
        assert(props["type"] == "SERVICE2")

        verifySequence {
            service.getConfig()
            config.publicKey
        }
    }
}
