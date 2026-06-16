package org.edu_sharing.rendering.modules.av

import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@ConditionalOnAvConverter
@Component
class AvFileHelperFactory(
    private val storageImplementation: StorageService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    fun createAvFileHelper(): AvFileHelper {
        log.debug("Creating new AvFileHelper instance")
        return AvFileHelper(storageImplementation)
    }
}