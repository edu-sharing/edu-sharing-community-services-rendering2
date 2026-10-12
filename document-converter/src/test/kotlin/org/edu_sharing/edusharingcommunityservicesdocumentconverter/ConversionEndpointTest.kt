package org.edu_sharing.edusharingcommunityservicesdocumentconverter

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.health.contributor.Status
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.File
import kotlin.test.assertEquals

/** End-to-end with a real LibreOffice; skipped when `soffice` is not installed (e.g. a bare CI image). */
@SpringBootTest(properties = ["jodconverter.local.port-numbers=2917", "management.server.port=0"])
@AutoConfigureMockMvc
class ConversionEndpointTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var health: org.edu_sharing.edusharingcommunityservicesdocumentconverter.health.LibreOfficeHealthIndicator
    @Autowired lateinit var tempFiles: org.edu_sharing.edusharingcommunityservicesdocumentconverter.io.TempFiles

    companion object {
        @JvmStatic
        @BeforeAll
        fun requireLibreOffice() {
            val found = System.getenv("PATH").orEmpty().split(File.pathSeparator)
                .any { File(it, "soffice").canExecute() }
            assumeTrue(found, "LibreOffice (soffice) not installed")
        }
    }

    @Test
    fun `txt is converted to pdf and the temp file is cleaned up`() {
        val file = MockMultipartFile("file", "Hello World.txt", "text/plain", "hello".toByteArray())
        mockMvc.perform(multipart("/conversion").file(file).param("format", "pdf"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Hello World.pdf")))
        java.nio.file.Files.list(tempFiles.workDir).use { assertEquals(0, it.count()) }
    }

    @Test
    fun `libreoffice health is up`() {
        assertEquals(Status.UP, health.health()?.status)
    }
}
