package org.edu_sharing.rendering.modules.binder.git

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class GitHubConfig {

    @Bean
    fun gitHubBinaryWebClient(): WebClient {
        return WebClient.create("https://raw.githubusercontent.com")
    }

    @Bean
    fun gitHubRepoApiWebClient(): WebClient {
        return WebClient.create("https://api.github.com/repos")
    }
}