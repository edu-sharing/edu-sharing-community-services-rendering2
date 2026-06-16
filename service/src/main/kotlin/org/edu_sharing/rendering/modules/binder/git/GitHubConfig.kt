package org.edu_sharing.rendering.modules.binder.git

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class GitHubConfig {

    @Bean
    fun gitHubBinaryWebClient(webClientBuilder: WebClient.Builder): WebClient {
        return webClientBuilder.clone().baseUrl("https://raw.githubusercontent.com").build()
    }

    @Bean
    fun gitHubRepoApiWebClient(webClientBuilder: WebClient.Builder): WebClient {
        return webClientBuilder.clone().baseUrl("https://api.github.com/repos").build()
    }
}