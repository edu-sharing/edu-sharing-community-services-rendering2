package org.edu_sharing.rendering.modules.jupyter

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@ConditionalOnConverter
@Configuration
class JupyterConverterConfig {
    @Value("\${app.jupyterConverter.host}")
    lateinit var converterBaseUrl: String

    @Bean
    fun jupyterConverterWebClient(): WebClient {
        return WebClient.builder().baseUrl(converterBaseUrl)
            .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }.build()
    }
}