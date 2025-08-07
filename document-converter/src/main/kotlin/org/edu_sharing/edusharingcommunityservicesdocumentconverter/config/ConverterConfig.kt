package org.edu_sharing.edusharingcommunityservicesdocumentconverter.config

import org.jodconverter.core.office.OfficeManager
import org.jodconverter.local.LocalConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration class for setting up the document converter.
 *
 * This class provides the necessary configuration to enable
 * document conversion using the specified OfficeManager.
 */
@Configuration
class ConverterConfig (
    private val officeManager: OfficeManager
){
    @Bean
    fun converter(): LocalConverter {
        return LocalConverter.builder().officeManager(officeManager).build()
    }
}
