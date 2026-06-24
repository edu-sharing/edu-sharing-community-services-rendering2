package org.edu_sharing.rendering.modules.jupyter

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.client.WebClient

@ConditionalOnConverter
@Configuration
class JupyterConverterConfig(

    @param:Value($$"${app.jupyterConverter.host}")
    private val converterBaseUrl: String,
    @param:Value($$"${spring.http.codecs.max-in-memory-size}")
    private val maxInMemorySize: DataSize
) {

    @Bean
    fun jupyterConverterWebClient(webClientBuilder: WebClient.Builder): WebClient {
        return webClientBuilder.clone()
            .baseUrl(converterBaseUrl)
            .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemorySize.toBytes().toInt()) }
            .build()
    }
}