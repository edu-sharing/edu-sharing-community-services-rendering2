package org.edu_sharing.rendering.integration.roles

import org.edu_sharing.rendering.cacheCleaner.CacheCleaner
import org.edu_sharing.rendering.integration.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils

@SpringBootTest(
    properties = [
        "app.roles=cache-cleaner",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false"
    ]
)
class CacheCleanerRoleTest(@Autowired val context: ApplicationContext): AbstractIntegrationTest() {

    companion object {
        private val roleSpecificBeans = setOf(
            CacheCleaner::class
        )
    }

    @Test
    fun testBeanConfiguration() {
        val allEdusharingBeans = context.beanDefinitionNames.filter {
            context.getBean(it).javaClass.packageName.startsWith("org.edu_sharing.rendering")
        }.toSet()

        val expectedBeans = roleSpecificBeans
            .map { ClassUtils.getShortNameAsProperty(it.java) } union SharedBeans.all
        assert(allEdusharingBeans == expectedBeans)
    }
}