package org.edu_sharing.rendering.modules.av

import org.edu_sharing.rendering.storage.StorageService
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