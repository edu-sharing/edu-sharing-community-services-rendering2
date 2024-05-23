package org.edu_sharing.rendering.processing.av

import org.edu_sharing.rendering.blobStorage.StorageService
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
class AvFileHelperFactory(
    private val storageImplementation: StorageService
) {
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    fun createAvFileHelper(): AvFileHelper {
        return AvFileHelper(storageImplementation)
    }
}