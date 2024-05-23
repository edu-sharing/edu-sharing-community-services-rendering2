package org.edu_sharing.rendering.processing.av

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ws.schild.jave.Encoder

@Configuration
class AvConfig {
    @Bean
    fun encoder(): Encoder = Encoder()
}