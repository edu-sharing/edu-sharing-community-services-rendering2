package org.edu_sharing.rendering.modules.jupyter

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@ConditionalOnConverter
@Configuration
class JupyterConverterConfig(

    @param:Value($$"${app.jupyterConverter.host}")
    private val converterBaseUrl: String,
) {

    @Bean
    fun jupyterConverterWebClient(
        @Qualifier("longRunningWebClientBuilder") webClientBuilder: WebClient.Builder
    ): WebClient {
        // Buffer limit is inherited from the shared builder (spring.http.codecs.max-in-memory-size).
        return webClientBuilder.clone()
            .baseUrl(converterBaseUrl)
            .build()
    }
}