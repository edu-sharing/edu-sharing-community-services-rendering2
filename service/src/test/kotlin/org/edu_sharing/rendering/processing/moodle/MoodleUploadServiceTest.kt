package org.edu_sharing.rendering.processing.moodle

import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.web.reactive.function.client.WebClient

class MoodleUploadServiceTest {
    companion object {
        const val BASE_URL = "http://localhost"
        const val PORT = 2105
        private lateinit var mockWebServer: MockWebServer

        @JvmStatic
        @BeforeAll
        fun init() {
            mockWebServer = MockWebServer()
            mockWebServer.start(PORT)
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            mockWebServer.shutdown()
        }
    }
    private val webClient = WebClient.create("$BASE_URL:$PORT")
    private val underTest = MoodleUploadService(webClient)

}