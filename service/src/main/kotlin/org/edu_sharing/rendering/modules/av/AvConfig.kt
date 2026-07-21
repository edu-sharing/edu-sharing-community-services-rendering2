package org.edu_sharing.rendering.modules.av

import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import ws.schild.jave.Encoder
import ws.schild.jave.process.ProcessLocator

@ConditionalOnAvConverter
@Configuration
class AvConfig {
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    fun encoder(ffmpegLocator: ProcessLocator): Encoder = Encoder(ffmpegLocator)
}