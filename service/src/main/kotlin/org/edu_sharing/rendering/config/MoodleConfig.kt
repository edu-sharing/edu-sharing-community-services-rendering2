package org.edu_sharing.rendering.config

import org.edu_sharing.rendering.config.annotation.ConditionalOnMoodle
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@ConditionalOnMoodle
class MoodleConfig() {
    @Value("\${app.moodle.host}")
    lateinit var moodleBaseUrl: String

    @Bean
    fun moodleWebClient(): WebClient {
        return WebClient.create(moodleBaseUrl)
    }
}