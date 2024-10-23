package org.edu_sharing.rendering.integration.roles

import org.edu_sharing.rendering.integration.AbstractIntegrationTest
import org.edu_sharing.rendering.modules.h5p.H5pReceiver
import org.edu_sharing.rendering.modules.h5p.H5pUploadService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils

@SpringBootTest(
    properties = [
        "app.roles=h5p",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false"
    ]
)
class H5pRoleTest(@Autowired val context: ApplicationContext): AbstractIntegrationTest() {
    
    companion object {
        private val roleSpecificBeans = setOf(
            H5pReceiver::class,
            H5pUploadService::class
        )
    }
    
    @Test
    fun testBeanConfiguration() {
        val allEdusharingBeans = context.beanDefinitionNames.filter {
            context.getBean(it).javaClass.packageName.startsWith("org.edu_sharing.rendering")
        }.toSet()

        val expectedBeans = roleSpecificBeans
            .map { ClassUtils.getShortNameAsProperty(it.java) } union SharedBeans.all

        assert(expectedBeans == allEdusharingBeans)
    }
}