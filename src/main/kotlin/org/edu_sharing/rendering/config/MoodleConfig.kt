package org.edu_sharing.rendering.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@ConditionalOnProperty(prefix = "app", name = ["moodle.basedir"])
class MoodleConfig {
    @Value("\${app.moodle.basedir}")
    lateinit var moodleBaseUrl: String

    @Bean
    fun moodleWebClient(): WebClient {
        return WebClient.builder().baseUrl(moodleBaseUrl).build()
    }
}