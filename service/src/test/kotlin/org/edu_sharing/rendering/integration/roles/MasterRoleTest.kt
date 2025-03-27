package org.edu_sharing.rendering.integration.roles

import org.edu_sharing.rendering.cacheCleaner.CacheCleaner
import org.edu_sharing.rendering.edusharingRepo.AdminController
import org.edu_sharing.rendering.integration.AbstractIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest(
    properties = [
        "app.roles=master",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false",
        "app.repository.registration.enabled=true"
    ]
)
class MasterRoleTest(@Autowired val context: ApplicationContext): AbstractIntegrationTest() {

    companion object {
        private val roleSpecificBeans = setOf(
            CacheCleaner::class,
            AdminController::class,
        )
    }
/*
    @Test
    fun testBeanConfiguration() {
        val allEdusharingBeans = context.beanDefinitionNames.filter {
            context.getBean(it).javaClass.packageName.startsWith("org.edu_sharing.rendering")
        }.toSet()

        val expectedBeans = roleSpecificBeans
            .map { ClassUtils.getShortNameAsProperty(it.java) } union SharedBeans.all

        assert(allEdusharingBeans == expectedBeans)
    }

 */
}