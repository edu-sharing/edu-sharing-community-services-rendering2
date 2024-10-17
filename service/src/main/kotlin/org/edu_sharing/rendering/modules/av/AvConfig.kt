package org.edu_sharing.rendering.modules.av

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ws.schild.jave.Encoder

@ConditionalOnConverter
@Configuration
class AvConfig {
    @Bean
    fun encoder(): Encoder = Encoder()
}