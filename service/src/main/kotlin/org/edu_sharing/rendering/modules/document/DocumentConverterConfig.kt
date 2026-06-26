package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@ConditionalOnConverter
@Configuration
class DocumentConverterConfig(
    @param:Value($$"${app.documentConverter.host}")
    private val converterBaseUrl: String,
) {

    @Bean
    fun documentConverterWebClient(
        @Qualifier("longRunningWebClientBuilder") webClientBuilder: WebClient.Builder
    ): WebClient {
        // Buffer limit is inherited from the shared builder (spring.http.codecs.max-in-memory-size).
        return webClientBuilder.clone()
            .baseUrl(converterBaseUrl)
            .build()
    }
}
