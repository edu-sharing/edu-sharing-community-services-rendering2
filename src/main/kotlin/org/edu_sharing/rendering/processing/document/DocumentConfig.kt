package org.edu_sharing.rendering.processing.document

import org.jodconverter.core.office.OfficeManager
import org.jodconverter.local.LocalConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DocumentConfig (
    private val officeManager: OfficeManager
) {
    @Bean
    fun converter(): LocalConverter {
        return LocalConverter.builder().officeManager(officeManager).build()
    }
}