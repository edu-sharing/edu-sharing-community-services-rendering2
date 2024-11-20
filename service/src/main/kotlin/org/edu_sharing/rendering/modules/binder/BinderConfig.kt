package org.edu_sharing.rendering.modules.binder

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class BinderConfig {
    @Value("\${app.binder.host}")
    lateinit var binderBaseUrl: String

    @Bean
    fun binderWebClient(): WebClient {
        return WebClient.builder().baseUrl(binderBaseUrl).build()
    }
}