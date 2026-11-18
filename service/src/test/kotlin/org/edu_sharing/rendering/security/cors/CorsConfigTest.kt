package org.edu_sharing.rendering.security.cors

import org.edu_sharing.rendering.config.AppInfo
import org.junit.jupiter.api.Test
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CorsConfigTest {

    private fun appInfo(
        publicProtocol: String = "https",
        publicHost: String = "renderer.example.org",
        publicPort: Int = 443,
    ): AppInfo {
        val appInfo = AppInfo()
        appInfo.public = connectionInfo(publicProtocol, publicHost, publicPort)
        appInfo.internal = connectionInfo("http", "rendering2-service", 8080)
        return appInfo
    }

    private fun connectionInfo(protocol: String, host: String, port: Int) =
        AppInfo.ConnectionInfo(protocol, host, port.toShort(), "/rendering", "$protocol://$host:$port/rendering")

    private fun CorsConfig.configuration(): CorsConfiguration {
        val source = corsConfigurationSource() as UrlBasedCorsConfigurationSource
        return assertNotNull(source.corsConfigurations["/**"])
    }

    @Test
    fun `the own public origin is allowed although the public url carries a context path`() {
        // Arrange
        val appInfo = appInfo()

        // Act
        val configuration = CorsConfig(appInfo, true).configuration()

        // Assert
        assertEquals("https://renderer.example.org", configuration.checkOrigin("https://renderer.example.org"))
    }

    @Test
    fun `the default http port is left out of the own origin`() {
        // Arrange
        val appInfo = appInfo(publicProtocol = "http", publicPort = 80)

        // Act
        val configuration = CorsConfig(appInfo, true).configuration()

        // Assert
        assertEquals("http://renderer.example.org", configuration.checkOrigin("http://renderer.example.org"))
    }

    @Test
    fun `a non default port is part of the own origin`() {
        // Arrange
        val appInfo = appInfo(publicProtocol = "http", publicPort = 8080)

        // Act
        val configuration = CorsConfig(appInfo, true).configuration()

        // Assert
        assertEquals(
            "http://renderer.example.org:8080",
            configuration.checkOrigin("http://renderer.example.org:8080")
        )
        assertNull(configuration.checkOrigin("http://renderer.example.org"))
    }

    @Test
    fun `the own internal origin is allowed`() {
        // Arrange
        val appInfo = appInfo()

        // Act
        val configuration = CorsConfig(appInfo, true).configuration()

        // Assert
        assertEquals("http://rendering2-service:8080", configuration.checkOrigin("http://rendering2-service:8080"))
    }

    @Test
    fun `a foreign origin is still rejected`() {
        // Arrange
        val appInfo = appInfo()

        // Act
        val configuration = CorsConfig(appInfo, true).configuration()

        // Assert
        assertNull(configuration.checkOrigin("https://foreign.example.org"))
    }

    @Test
    fun `origins from the repository are still allowed next to the own origin`() {
        // Arrange
        val corsConfig = CorsConfig(appInfo(), true)

        // Act
        corsConfig.updateAllowedOrigins(setOf("https://repository.example.org"))
        corsConfig.updateCorsConfiguration()
        val configuration = corsConfig.configuration()

        // Assert
        assertEquals("https://repository.example.org", configuration.checkOrigin("https://repository.example.org"))
        assertEquals("https://renderer.example.org", configuration.checkOrigin("https://renderer.example.org"))
    }
}
