package org.edu_sharing.rendering.modules.h5p.lumi

import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class LumiConfig() {
    @Value("\${app.lumi.host}${H5P_BASE_PATH}")
    lateinit var lumiBaseUrl: String

    @Bean
    fun lumiWebClient(): WebClient {
        return WebClient.builder().baseUrl(lumiBaseUrl).build()
    }
}