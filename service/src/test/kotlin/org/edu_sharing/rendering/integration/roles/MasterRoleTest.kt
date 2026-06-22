package org.edu_sharing.rendering.integration.roles

import org.edu_sharing.rendering.asset.AdminAssetController
import org.edu_sharing.rendering.cacheCleaner.CacheCleaner
import org.edu_sharing.rendering.edusharingRepo.AdminController
import org.edu_sharing.rendering.edusharingRepo.AdminStorageController
import org.edu_sharing.rendering.edusharingRepo.cors.CorsAllowedOriginsReceiver
import org.edu_sharing.rendering.renderingJob.AdminJobController
import org.edu_sharing.rendering.edusharingRepo.cors.CorsSyncScheduler
import org.edu_sharing.rendering.edusharingRepo.cors.CorsSyncService
import org.edu_sharing.rendering.integration.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.util.ClassUtils

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
class MasterRoleTest(
    @param:Autowired val context: ApplicationContext
): AbstractIntegrationTest() {

    companion object {
        private val roleSpecificBeans = setOf(
            AdminController::class,
            AdminStorageController::class,
            AdminJobController::class,
            AdminAssetController::class,
            CacheCleaner::class,
            CorsAllowedOriginsReceiver::class,
            CorsSyncScheduler::class,
            CorsSyncService::class
        )
    }

    @Test
    fun testBeanConfiguration() {
        val allEdusharingBeans = context.beanDefinitionNames.filter {
            context.getBean(it).javaClass.packageName.startsWith("org.edu_sharing.rendering")
        }.toSet()

        val expectedBeans = roleSpecificBeans
            .map { ClassUtils.getShortNameAsProperty(it.java) } union SharedBeans.all

        val beanMatch = allEdusharingBeans == expectedBeans
        if (! beanMatch) {
            val missingBeans = expectedBeans subtract allEdusharingBeans
            val unexpectedBeans = allEdusharingBeans subtract expectedBeans

            println("Missing beans (expected but not found): $missingBeans")
            println("Unexpected beans (found but not expected): $unexpectedBeans")
        }

        assert(beanMatch)
    }
}
