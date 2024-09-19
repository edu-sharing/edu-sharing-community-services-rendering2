package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@ConditionalOnConverter
@Configuration
class DocumentConverterConfig {
    @Value("\${app.documentConverter.host}")
    lateinit var converterBaseUrl: String

    @Bean
    fun documentConverterWebClient(): WebClient {
        return WebClient.builder().baseUrl(converterBaseUrl).build()
    }
}