package org.edu_sharing.rendering.config

import org.edu_sharing.rendering.config.annotation.ConditionalOnH5p
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@ConditionalOnH5p
class LumiConfig(private val errorHandler: ExchangeFilterFunction) {
    @Value("\${app.lumi.host}")
    lateinit var lumiBaseUrl: String

    @Bean
    fun lumiWebClient(errorHandler: ExchangeFilterFunction): WebClient {
        return WebClient.builder().baseUrl(lumiBaseUrl).build()
    }
}