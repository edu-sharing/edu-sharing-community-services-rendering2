package org.edu_sharing.rendering.config

import org.edu_sharing.rendering.config.annotation.ConditionalOnH5p
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@ConditionalOnH5p
class LumiConfig() {
    @Value("\${app.lumi.host}")
    lateinit var lumiBaseUrl: String

    @Bean
    fun lumiWebClient(): WebClient {
        return WebClient.create(lumiBaseUrl)
    }
}